package io.vertxrpc0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServerOptions;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertxrpc0.client.ServiceFactory;
import io.vertxrpc0.client.ServiceFactoryBuilder;
import io.vertxrpc0.server.Rpc0Server;
import io.vertxrpc0.server.Rpc0ServerBuilder;
import io.vertxrpc0.service.HelloService;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * End-to-end test for {@code Rpc0ServerBuilder.buildSupplier()} and {@code
 * ServiceFactoryBuilder.buildSupplier()}: deploys {@value #SERVER_INSTANCES} server verticles
 * behind one port via Vert.x port-sharing, then {@value #CLIENT_VERTICLES} client verticles each
 * minting its own factory inside {@code start()}. Asserts traffic reaches more than one server
 * event loop.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 30, timeUnit = TimeUnit.SECONDS)
public class MultiVerticleDeploymentTest {

  private static final int SERVER_INSTANCES = 4;
  private static final int CLIENT_VERTICLES = 4;
  private static final int CALLS_PER_VERTICLE = 5;

  private static int findFreePort() {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void multiInstanceServerAndClientsDistributeTraffic(Vertx vertx, VertxTestContext ctx) {
    int port = findFreePort();

    ThreadCountingHello impl = new ThreadCountingHello();
    Supplier<Rpc0Server> serverSupplier =
        new Rpc0ServerBuilder(vertx, new NetServerOptions().setHost("127.0.0.1").setPort(port))
            .addBinding(HelloService.class, impl)
            .buildSupplier();

    Supplier<ServiceFactory> clientSupplier =
        new ServiceFactoryBuilder(vertx, "127.0.0.1", port)
            .registerService(HelloService.class)
            .buildSupplier();

    List<String> responses = new CopyOnWriteArrayList<>();
    int totalCalls = CLIENT_VERTICLES * CALLS_PER_VERTICLE;
    AtomicInteger callsDone = new AtomicInteger();
    Promise<Void> allDone = Promise.promise();

    Supplier<AbstractVerticle> clientVerticleSupplier =
        () ->
            new AbstractVerticle() {
              @Override
              public void start() {
                ServiceFactory factory = clientSupplier.get();
                HelloService hello = factory.create(HelloService.class);
                for (int i = 0; i < CALLS_PER_VERTICLE; i++) {
                  hello
                      .sayHello("call-" + i)
                      .onComplete(
                          ar -> {
                            if (ar.succeeded()) {
                              responses.add(ar.result());
                            }
                            if (callsDone.incrementAndGet() == totalCalls) {
                              allDone.tryComplete();
                            }
                          });
                }
              }
            };

    vertx
        .deployVerticle(serverSupplier::get, new DeploymentOptions().setInstances(SERVER_INSTANCES))
        .compose(
            serverDep ->
                vertx.deployVerticle(
                    clientVerticleSupplier::get,
                    new DeploymentOptions().setInstances(CLIENT_VERTICLES)))
        .compose(clientDep -> allDone.future())
        .onComplete(
            ctx.succeeding(
                v ->
                    ctx.verify(
                        () -> {
                          assertEquals(
                              totalCalls, responses.size(), "expected every RPC to succeed");
                          for (String r : responses) {
                            assertTrue(r.startsWith("Hello, "), () -> "unexpected response: " + r);
                          }
                          assertTrue(
                              impl.handlerThreads.size() >= 2,
                              () ->
                                  "expected traffic on >= 2 server event loops, but only saw: "
                                      + impl.handlerThreads);
                          ctx.completeNow();
                        })));
  }

  public static final class ThreadCountingHello implements HelloService {
    final Set<String> handlerThreads = new ConcurrentSkipListSet<>();

    @Override
    public Future<String> sayHello(String name) {
      handlerThreads.add(Thread.currentThread().getName());
      return Future.succeededFuture("Hello, " + name);
    }
  }
}
