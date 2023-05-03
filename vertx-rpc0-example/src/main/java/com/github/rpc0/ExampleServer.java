package com.github.rpc0;

import com.github.rpc0.service.BeanService;
import com.github.rpc0.service.DoubleService;
import com.github.rpc0.service.HelloService;
import com.github.rpc0.service.StringService;
import com.github.rpc0.service.TimeService;
import com.github.rpc0.service.VoidService;
import com.github.rpc0.service.impl.BeanServiceImpl;
import com.github.rpc0.service.impl.DoubleServiceImpl;
import com.github.rpc0.service.impl.HelloServiceImpl;
import com.github.rpc0.service.impl.StringServiceImpl;
import com.github.rpc0.service.impl.TimeServiceImpl;
import com.github.rpc0.service.impl.VoidServiceImpl;
import com.github.rpc0.util.ObjectMapperSupplier;
import com.github.rpc0.server.Rpc0Server;
import com.github.rpc0.server.Rpc0ServerBuilder;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServerOptions;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * @author fishzhao
 * @since 2022-01-20
 */
@Slf4j
public final class ExampleServer {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    Rpc0Server rpc0Server = new Rpc0ServerBuilder(vertx, new NetServerOptions().setHost(args[0]).setPort(Integer.parseInt(args[1])))
            .addBinding(StringService.class, new StringServiceImpl())
            .addBinding(DoubleService.class, new DoubleServiceImpl())
            .addBinding(TimeService.class, new TimeServiceImpl())
            .addBinding(VoidService.class, new VoidServiceImpl(vertx))
            .addBinding(BeanService.class, new BeanServiceImpl(new ObjectMapperSupplier().get()))
            .addBinding(HelloService.class, new HelloServiceImpl())
            .registerTypes("com.github.rpc0.model", false)
            //.setMaxInactiveDuration(Duration.ofSeconds(5))
            .build();
    vertx.deployVerticle(rpc0Server)
            .onComplete(result -> {
              if (result.succeeded()) {
                log.info("Listening on: {}", Arrays.toString(args));
                Runtime.getRuntime().addShutdownHook(new Thread(() -> vertx.undeploy(result.result())));
              } else {
                log.error("Launch server with exception: ", result.cause());
              }
            });
  }
}
