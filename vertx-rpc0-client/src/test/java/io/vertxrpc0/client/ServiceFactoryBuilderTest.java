package io.vertxrpc0.client;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetClientOptions;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(VertxExtension.class)
public class ServiceFactoryBuilderTest {

  interface DemoService {}
  static final class NotAnInterface {}

  @Test
  public void registerServiceAcceptsInterface(Vertx vertx) {
    ServiceFactoryBuilder builder = new ServiceFactoryBuilder(vertx, "localhost", 7777);
    builder.registerService(DemoService.class);
  }

  @Test
  public void registerServiceRejectsConcreteClass(Vertx vertx) {
    ServiceFactoryBuilder builder = new ServiceFactoryBuilder(vertx, "localhost", 7777);
    assertThrows(IllegalArgumentException.class, () -> builder.registerService(NotAnInterface.class));
  }

  @Test
  public void registerServiceIsIdempotent(Vertx vertx) {
    ServiceFactoryBuilder builder = new ServiceFactoryBuilder(vertx, "localhost", 7777);
    builder.registerService(DemoService.class);
    builder.registerService(DemoService.class);
  }

  @Test
  public void buildProducesUsableFactory(Vertx vertx) {
    ServiceFactoryBuilder builder = new ServiceFactoryBuilder(vertx, "localhost", 7777,
            new NetClientOptions(), Duration.ofSeconds(1), getClass().getClassLoader());
    builder.registerService(DemoService.class);
    ServiceFactory factory = builder.build();
    assertNotNull(factory);
  }

  @Test
  public void constructorRejectsNullVertx() {
    assertThrows(NullPointerException.class, () -> new ServiceFactoryBuilder(null, "localhost", 7777));
  }

  @Test
  public void constructorRejectsNullHost(Vertx vertx) {
    assertThrows(NullPointerException.class, () -> new ServiceFactoryBuilder(vertx, null, 7777));
  }
}
