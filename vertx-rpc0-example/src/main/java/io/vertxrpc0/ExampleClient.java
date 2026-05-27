package io.vertxrpc0;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetClientOptions;
import io.vertxrpc0.client.ServiceFactory;
import io.vertxrpc0.client.ServiceFactoryBuilder;
import io.vertxrpc0.service.BeanService;
import io.vertxrpc0.service.DoubleService;
import io.vertxrpc0.service.HelloService;
import io.vertxrpc0.service.StringService;
import io.vertxrpc0.service.TimeService;
import io.vertxrpc0.service.VoidService;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author fishzhao
 * @since 2022-01-20
 */
public final class ExampleClient {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    ServiceFactory factory =
        new ServiceFactoryBuilder(
                vertx,
                "127.0.0.1",
                9549,
                new NetClientOptions(),
                Duration.ofSeconds(3),
                Vertx.class.getClassLoader())
            .registerService(DoubleService.class)
            .registerService(StringService.class)
            .registerService(TimeService.class)
            .registerService(VoidService.class)
            .registerService(BeanService.class)
            .registerService(HelloService.class)
            .registerTypes("io.vertxrpc0.model", false)
            .build();
    AtomicBoolean stopped = new AtomicBoolean(false);
    HelloService helloService = factory.create(HelloService.class);
    vertx.setPeriodic(
        1000,
        timeId -> {
          if (stopped.get()) {
            vertx.cancelTimer(timeId);
            return;
          }
          String s = UUID.randomUUID().toString();
          long start = System.currentTimeMillis();
          helloService
              .sayHello(s)
              .onComplete(
                  result -> {
                    if (result.succeeded()) {
                      System.out.printf(
                          "%s => %s: %dms%n",
                          s, result.result(), System.currentTimeMillis() - start);
                    } else {
                      result.cause().printStackTrace();
                    }
                  });
        });
    Runtime.getRuntime().addShutdownHook(new Thread(() -> stopped.set(true)));
  }
}
