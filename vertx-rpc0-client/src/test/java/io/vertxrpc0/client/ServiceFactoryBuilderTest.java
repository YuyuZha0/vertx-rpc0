package io.vertxrpc0.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.NetClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class ServiceFactoryBuilderTest {

  private static ContextInternal readSupplierContext(ServiceFactory factory) {
    try {
      Field supplierField = ServiceFactory.class.getDeclaredField("proxyStubSupplier");
      supplierField.setAccessible(true);
      Object stubSupplier = supplierField.get(factory);
      Field contextField = stubSupplier.getClass().getDeclaredField("context");
      contextField.setAccessible(true);
      return (ContextInternal) contextField.get(stubSupplier);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void registerServiceAcceptsInterface(Vertx vertx) {
    ServiceFactoryBuilder builder = new ServiceFactoryBuilder(vertx, "localhost", 7777);
    builder.registerService(DemoService.class);
  }

  @Test
  public void registerServiceRejectsConcreteClass(Vertx vertx) {
    ServiceFactoryBuilder builder = new ServiceFactoryBuilder(vertx, "localhost", 7777);
    assertThrows(
        IllegalArgumentException.class, () -> builder.registerService(NotAnInterface.class));
  }

  @Test
  public void registerServiceIsIdempotent(Vertx vertx) {
    ServiceFactoryBuilder builder = new ServiceFactoryBuilder(vertx, "localhost", 7777);
    builder.registerService(DemoService.class);
    builder.registerService(DemoService.class);
  }

  @Test
  public void buildProducesUsableFactory(Vertx vertx) {
    ServiceFactoryBuilder builder =
        new ServiceFactoryBuilder(
            vertx,
            "localhost",
            7777,
            new NetClientOptions(),
            Duration.ofSeconds(1),
            getClass().getClassLoader());
    builder.registerService(DemoService.class);
    ServiceFactory factory = builder.build();
    assertNotNull(factory);
  }

  @Test
  public void constructorRejectsNullVertx() {
    assertThrows(
        NullPointerException.class, () -> new ServiceFactoryBuilder(null, "localhost", 7777));
  }

  @Test
  public void constructorRejectsNullHost(Vertx vertx) {
    assertThrows(NullPointerException.class, () -> new ServiceFactoryBuilder(vertx, null, 7777));
  }

  @Test
  public void setMaxMsgLenRejectsNonPositive(Vertx vertx) {
    ServiceFactoryBuilder builder = new ServiceFactoryBuilder(vertx, "localhost", 7777);
    assertThrows(IllegalArgumentException.class, () -> builder.setMaxMsgLen(0));
    assertThrows(IllegalArgumentException.class, () -> builder.setMaxMsgLen(-1));
  }

  @Test
  public void buildSupplierProducesFreshInstancesPerCall(Vertx vertx) {
    Supplier<ServiceFactory> supplier =
        new ServiceFactoryBuilder(vertx, "localhost", 7777)
            .registerService(DemoService.class)
            .buildSupplier();
    ServiceFactory a = supplier.get();
    ServiceFactory b = supplier.get();
    assertNotNull(a);
    assertNotNull(b);
    assertNotSame(a, b);
  }

  // === buildSupplier ===

  @Test
  public void buildSupplierSnapshotsConfig(Vertx vertx) throws Exception {
    ServiceFactoryBuilder builder =
        new ServiceFactoryBuilder(vertx, "localhost", 7777).registerService(DemoService.class);
    Supplier<ServiceFactory> supplier = builder.buildSupplier();

    // Mutate the builder after snapshotting.
    builder.registerService(OtherService.class);

    ServiceFactory factory = supplier.get();
    Field registryField = ServiceFactory.class.getDeclaredField("registry");
    registryField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Set<Class<?>> services = (Set<Class<?>>) registryField.get(factory);
    assertEquals(1, services.size());
    assertTrue(services.contains(DemoService.class));
  }

  /**
   * The supplier path must defer {@code vertx.getOrCreateContext()} to {@code get()} call time, not
   * snapshot it at {@code buildSupplier()} time. Otherwise a Verticle calling the supplier in its
   * {@code start()} would inherit the builder's context instead of its own. Deploying a Verticle is
   * the cleanest way to force a separate context.
   */
  @Test
  public void buildSupplierCapturesContextAtCallTime(Vertx vertx, VertxTestContext ctx) {
    Supplier<ServiceFactory> supplier =
        new ServiceFactoryBuilder(vertx, "localhost", 7777)
            .registerService(DemoService.class)
            .buildSupplier();

    // Capture the supplier-creation-time (test) context for comparison.
    ContextInternal outerCtx = (ContextInternal) vertx.getOrCreateContext();

    AtomicReference<ContextInternal> verticleCtxRef = new AtomicReference<>();
    AtomicReference<ServiceFactory> factoryRef = new AtomicReference<>();

    vertx
        .deployVerticle(
            new io.vertx.core.AbstractVerticle() {
              @Override
              public void start() {
                verticleCtxRef.set((ContextInternal) context);
                factoryRef.set(supplier.get());
              }
            })
        .onComplete(
            ctx.succeeding(
                deploymentId ->
                    ctx.verify(
                        () -> {
                          ContextInternal verticleCtx = verticleCtxRef.get();
                          ContextInternal captured = readSupplierContext(factoryRef.get());
                          assertSame(
                              verticleCtx,
                              captured,
                              "supplier must capture the calling Verticle's context");
                          assertNotSame(
                              outerCtx,
                              captured,
                              "supplier must NOT capture the builder-thread's context");
                          ctx.completeNow();
                        })));
  }

  interface DemoService {}

  interface OtherService {}

  static final class NotAnInterface {}
}
