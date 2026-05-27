package io.vertxrpc0;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertxrpc0.client.ServiceFactory;
import io.vertxrpc0.client.ServiceFactoryBuilder;
import io.vertxrpc0.service.HelloService;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
@Timeout(value = 15, timeUnit = TimeUnit.SECONDS)
public class ProxyStubTimeoutTest {

  private NetServer silentServer;
  private ServiceFactory factory;

  @BeforeEach
  public void setUp(Vertx vertx, VertxTestContext ctx) {
    // A TCP server that accepts connections but never replies — clients should time out.
    NetServer s = vertx.createNetServer(new NetServerOptions().setHost("127.0.0.1").setPort(0));
    s.connectHandler(
        sock -> {
          /* deliberately silent */
        });
    s.listen()
        .onComplete(
            ctx.succeeding(
                listening -> {
                  silentServer = listening;
                  factory =
                      new ServiceFactoryBuilder(
                              vertx,
                              "127.0.0.1",
                              listening.actualPort(),
                              new NetClientOptions(),
                              Duration.ofMillis(300),
                              Vertx.class.getClassLoader())
                          .registerService(HelloService.class)
                          .build();
                  ctx.completeNow();
                }));
  }

  @AfterEach
  public void tearDown(VertxTestContext ctx) {
    Promise<Void> factoryClose = Promise.promise();
    factory.close(factoryClose);
    factoryClose
        .future()
        .recover(t -> io.vertx.core.Future.succeededFuture())
        .compose(v -> silentServer.close())
        .onComplete(ctx.succeedingThenComplete());
  }

  @Test
  public void rpcCallFailsWithTimeoutMessage(VertxTestContext ctx) {
    HelloService hello = factory.create(HelloService.class);
    long start = System.currentTimeMillis();
    hello
        .sayHello("test")
        .onComplete(
            ctx.failing(
                cause -> {
                  long elapsed = System.currentTimeMillis() - start;
                  ctx.verify(
                      () -> {
                        assertNotNull(cause);
                        assertTrue(
                            cause.getMessage().toLowerCase().contains("timeout"),
                            () ->
                                "expected 'Timeout' in failure message, got: "
                                    + cause.getMessage());
                        assertTrue(
                            elapsed >= 250 && elapsed < 3000,
                            () -> "elapsed=" + elapsed + " should be close to the 300ms timeout");
                      });
                  ctx.completeNow();
                }));
  }
}
