package io.vertxrpc0.testutil;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertxrpc0.client.ServiceFactory;
import io.vertxrpc0.client.ServiceFactoryBuilder;
import io.vertxrpc0.server.Rpc0Server;
import io.vertxrpc0.server.Rpc0ServerBuilder;
import io.vertxrpc0.service.BeanService;
import io.vertxrpc0.service.DoubleService;
import io.vertxrpc0.service.HelloService;
import io.vertxrpc0.service.StringService;
import io.vertxrpc0.service.TimeService;
import io.vertxrpc0.service.VoidService;
import io.vertxrpc0.service.impl.BeanServiceImpl;
import io.vertxrpc0.service.impl.DoubleServiceImpl;
import io.vertxrpc0.service.impl.HelloServiceImpl;
import io.vertxrpc0.service.impl.StringServiceImpl;
import io.vertxrpc0.service.impl.TimeServiceImpl;
import io.vertxrpc0.service.impl.VoidServiceImpl;
import io.vertxrpc0.util.ObjectMapperSupplier;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class Rpc0TestHarness {

  private final Vertx vertx;
  private final ObjectMapper objectMapper = new ObjectMapperSupplier().get();
  private final ServiceFactory factory;
  private final int port;
  private final String deploymentId;

  private Rpc0TestHarness(Vertx vertx, ServiceFactory factory, int port, String deploymentId) {
    this.vertx = vertx;
    this.factory = factory;
    this.port = port;
    this.deploymentId = deploymentId;
  }

  public static Rpc0TestHarness start(boolean ssl) throws Exception {
    Vertx vertx = Vertx.vertx();
    ObjectMapper objectMapper = new ObjectMapperSupplier().get();
    SelfSignedCertificate certificate = ssl ? SelfSignedCertificate.create() : null;

    NetServerOptions serverOptions = new NetServerOptions()
            .setHost("127.0.0.1")
            .setPort(0);
    if (ssl) {
      serverOptions
              .setSsl(true)
              .setKeyCertOptions(certificate.keyCertOptions())
              .setTrustOptions(certificate.trustOptions());
    }

    Rpc0Server server = new Rpc0ServerBuilder(vertx, serverOptions)
            .addBinding(StringService.class, new StringServiceImpl())
            .addBinding(DoubleService.class, new DoubleServiceImpl())
            .addBinding(TimeService.class, new TimeServiceImpl())
            .addBinding(VoidService.class, new VoidServiceImpl(vertx))
            .addBinding(BeanService.class, new BeanServiceImpl(objectMapper))
            .addBinding(HelloService.class, new HelloServiceImpl())
            .registerTypes("io.vertxrpc0.model", false)
            .build();

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> deploymentRef = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    vertx.deployVerticle(server).onComplete(ar -> {
      if (ar.succeeded()) {
        deploymentRef.set(ar.result());
      } else {
        error.set(ar.cause());
      }
      latch.countDown();
    });
    if (!latch.await(10, TimeUnit.SECONDS)) {
      throw new IllegalStateException("server start timed out");
    }
    if (error.get() != null) {
      throw new RuntimeException(error.get());
    }

    int actualPort = readActualPort(server);

    NetClientOptions clientOptions = new NetClientOptions();
    if (ssl) {
      clientOptions
              .setSsl(true)
              .setHostnameVerificationAlgorithm("")
              .setKeyCertOptions(certificate.keyCertOptions())
              .setTrustOptions(certificate.trustOptions());
    }
    ServiceFactory factory = new ServiceFactoryBuilder(vertx, "127.0.0.1", actualPort,
            clientOptions, Duration.ofSeconds(3), Vertx.class.getClassLoader())
            .registerService(DoubleService.class)
            .registerService(StringService.class)
            .registerService(TimeService.class)
            .registerService(VoidService.class)
            .registerService(BeanService.class)
            .registerService(HelloService.class)
            .registerTypes("io.vertxrpc0.model", false)
            .build();

    return new Rpc0TestHarness(vertx, factory, actualPort, deploymentRef.get());
  }

  private static int readActualPort(Rpc0Server server) {
    try {
      java.lang.reflect.Field f = Rpc0Server.class.getDeclaredField("netServer");
      f.setAccessible(true);
      io.vertx.core.net.NetServer netServer = (io.vertx.core.net.NetServer) f.get(server);
      return netServer.actualPort();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  public Vertx vertx() {
    return vertx;
  }

  public ServiceFactory factory() {
    return factory;
  }

  public ObjectMapper objectMapper() {
    return objectMapper;
  }

  public int port() {
    return port;
  }

  public void close() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    vertx.undeploy(deploymentId).onComplete(ar -> vertx.close().onComplete(v -> latch.countDown()));
    latch.await(5, TimeUnit.SECONDS);
  }
}
