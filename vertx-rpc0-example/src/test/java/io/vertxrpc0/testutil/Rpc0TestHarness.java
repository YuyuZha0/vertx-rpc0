package io.vertxrpc0.testutil;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
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

  /**
   * Bootstraps the server + client factory on the supplied {@link Vertx}.
   * The caller owns the Vertx lifecycle (typically provided by
   * {@code @ExtendWith(VertxExtension.class)}); {@link #close()} only undeploys
   * the server verticle.
   *
   * <p>Returns a Future that completes when the verticle is listening on its
   * ephemeral port and the client factory is built — no test-thread blocking.
   */
  public static Future<Rpc0TestHarness> start(Vertx vertx, boolean ssl) {
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

    return vertx.deployVerticle(server).map(deploymentId -> {
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

      return new Rpc0TestHarness(vertx, factory, actualPort, deploymentId);
    });
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

  /**
   * Returns a {@link Future} that completes once the server verticle is
   * undeployed. Does <strong>not</strong> close the {@link Vertx} — that's
   * the caller's responsibility.
   */
  public Future<Void> close() {
    return vertx.undeploy(deploymentId);
  }
}
