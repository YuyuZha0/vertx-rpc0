package io.vertxrpc0.client;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetClientOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ServiceFactoryBuilderTest {

  interface DemoService {}
  static final class NotAnInterface {}

  private Vertx vertx;

  @BeforeEach
  public void setUp() {
    vertx = Vertx.vertx();
  }

  @AfterEach
  public void tearDown() throws Exception {
    vertx.close().toCompletionStage().toCompletableFuture().get();
  }

  @Test
  public void registerServiceAcceptsInterface() {
    ServiceFactoryBuilder builder = new ServiceFactoryBuilder(vertx, "localhost", 7777);
    builder.registerService(DemoService.class);
  }

  @Test
  public void registerServiceRejectsConcreteClass() {
    ServiceFactoryBuilder builder = new ServiceFactoryBuilder(vertx, "localhost", 7777);
    assertThrows(IllegalArgumentException.class, () -> builder.registerService(NotAnInterface.class));
  }

  @Test
  public void registerServiceIsIdempotent() {
    ServiceFactoryBuilder builder = new ServiceFactoryBuilder(vertx, "localhost", 7777);
    builder.registerService(DemoService.class);
    builder.registerService(DemoService.class);
  }

  @Test
  public void buildProducesUsableFactory() {
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
  public void constructorRejectsNullHost() {
    assertThrows(NullPointerException.class, () -> new ServiceFactoryBuilder(vertx, null, 7777));
  }
}
