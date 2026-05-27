package io.vertxrpc0;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertxrpc0.client.ServiceFactory;
import io.vertxrpc0.client.ServiceFactoryBuilder;
import io.vertxrpc0.service.HelloService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 15, unit = TimeUnit.SECONDS)
public class ProxyStubTimeoutTest {

  private Vertx vertx;
  private NetServer silentServer;
  private ServiceFactory factory;

  @BeforeEach
  public void setUp() throws Exception {
    vertx = Vertx.vertx();

    // A TCP server that accepts connections but never replies — clients should time out.
    Promise<NetServer> listenP = Promise.promise();
    NetServer s = vertx.createNetServer(new NetServerOptions().setHost("127.0.0.1").setPort(0));
    s.connectHandler(sock -> { /* deliberately silent */ });
    s.listen().onComplete(ar -> {
      if (ar.succeeded()) listenP.complete(ar.result());
      else listenP.fail(ar.cause());
    });
    silentServer = listenP.future().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);

    factory = new ServiceFactoryBuilder(vertx, "127.0.0.1", silentServer.actualPort(),
            new NetClientOptions(), Duration.ofMillis(300), Vertx.class.getClassLoader())
            .registerService(HelloService.class)
            .build();
  }

  @AfterEach
  public void tearDown() throws Exception {
    Promise<Void> p = Promise.promise();
    factory.close(p);
    p.future().toCompletionStage().toCompletableFuture().get(2, TimeUnit.SECONDS);
    silentServer.close().toCompletionStage().toCompletableFuture().get(2, TimeUnit.SECONDS);
    vertx.close().toCompletionStage().toCompletableFuture().get(2, TimeUnit.SECONDS);
  }

  @Test
  public void rpcCallFailsWithTimeoutMessage() throws Exception {
    HelloService hello = factory.create(HelloService.class);
    Future<String> call = hello.sayHello("test");
    CompletableFuture<String> cf = call.toCompletionStage().toCompletableFuture();

    long start = System.currentTimeMillis();
    try {
      cf.get(5, TimeUnit.SECONDS);
      throw new AssertionError("expected timeout failure");
    } catch (ExecutionException e) {
      long elapsed = System.currentTimeMillis() - start;
      assertNotNull(e.getCause());
      assertTrue(e.getCause().getMessage().toLowerCase().contains("timeout"),
              () -> "expected 'Timeout' in failure message, got: " + e.getCause().getMessage());
      assertTrue(elapsed >= 250 && elapsed < 3000,
              () -> "elapsed=" + elapsed + " should be close to the 300ms timeout");
    }
  }
}
