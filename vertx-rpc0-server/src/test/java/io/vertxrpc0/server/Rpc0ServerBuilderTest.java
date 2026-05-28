package io.vertxrpc0.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServerOptions;
import io.vertx.junit5.VertxExtension;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class Rpc0ServerBuilderTest {

  @Test
  public void buildWithoutAnyBindingIsRejected(Vertx vertx) {
    Rpc0ServerBuilder builder = new Rpc0ServerBuilder(vertx, new NetServerOptions());
    assertThrows(IllegalArgumentException.class, builder::build);
  }

  @Test
  public void addBindingRejectsConcreteClass(Vertx vertx) {
    Rpc0ServerBuilder builder = new Rpc0ServerBuilder(vertx, new NetServerOptions());
    assertThrows(
        IllegalArgumentException.class,
        () -> builder.addBinding((Class) DemoServiceImpl.class, new DemoServiceImpl()));
  }

  @Test
  public void buildProducesUsableServer(Vertx vertx) {
    Rpc0ServerBuilder builder = new Rpc0ServerBuilder(vertx, new NetServerOptions());
    builder.addBinding(DemoService.class, new DemoServiceImpl());
    Rpc0Server server = builder.build();
    assertNotNull(server);
  }

  @Test
  public void constructorRejectsNullVertx() {
    assertThrows(
        NullPointerException.class, () -> new Rpc0ServerBuilder(null, new NetServerOptions()));
  }

  @Test
  public void setKeepAliveDurationRejectsNull(Vertx vertx) {
    Rpc0ServerBuilder builder = new Rpc0ServerBuilder(vertx, new NetServerOptions());
    assertThrows(NullPointerException.class, () -> builder.setKeepAliveDuration(null));
  }

  @Test
  public void setMaxMsgLenRejectsNonPositive(Vertx vertx) {
    Rpc0ServerBuilder builder = new Rpc0ServerBuilder(vertx, new NetServerOptions());
    assertThrows(IllegalArgumentException.class, () -> builder.setMaxMsgLen(0));
    assertThrows(IllegalArgumentException.class, () -> builder.setMaxMsgLen(-1));
  }

  @Test
  public void buildSupplierProducesFreshInstancesPerCall(Vertx vertx) {
    Rpc0ServerBuilder builder =
        new Rpc0ServerBuilder(vertx, new NetServerOptions())
            .addBinding(DemoService.class, new DemoServiceImpl());
    Supplier<Rpc0Server> supplier = builder.buildSupplier();
    Rpc0Server a = supplier.get();
    Rpc0Server b = supplier.get();
    assertNotNull(a);
    assertNotNull(b);
    assertNotSame(a, b);
  }

  @Test
  public void buildSupplierEagerlyRejectsEmptyRegistry(Vertx vertx) {
    Rpc0ServerBuilder builder = new Rpc0ServerBuilder(vertx, new NetServerOptions());
    assertThrows(IllegalArgumentException.class, builder::buildSupplier);
  }

  // === buildSupplier ===

  @Test
  public void buildSupplierSnapshotsConfig(Vertx vertx) throws Exception {
    Rpc0ServerBuilder builder =
        new Rpc0ServerBuilder(vertx, new NetServerOptions())
            .addBinding(DemoService.class, new DemoServiceImpl())
            .setKeepAliveDuration(Duration.ofSeconds(7));
    Supplier<Rpc0Server> supplier = builder.buildSupplier();

    // Mutate the builder after snapshotting.
    builder.addBinding(OtherService.class, new OtherServiceImpl());
    builder.setKeepAliveDuration(Duration.ofSeconds(99));

    Rpc0Server server = supplier.get();

    // The snapshotted keepAliveMills must match the original 7s, not the mutated 99s.
    Field keepAliveField = Rpc0Server.class.getDeclaredField("keepAliveMills");
    keepAliveField.setAccessible(true);
    assertEquals(Duration.ofSeconds(7).toMillis(), keepAliveField.getLong(server));

    // The snapshotted service map must contain only DemoService.
    Field lookupField = Rpc0Server.class.getDeclaredField("serviceLookup");
    lookupField.setAccessible(true);
    ServiceLookup lookup = (ServiceLookup) lookupField.get(server);
    Field serviceMapField = ServiceLookup.class.getDeclaredField("serviceMap");
    serviceMapField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Object> services =
        (java.util.Map<String, Object>) serviceMapField.get(lookup);
    assertEquals(1, services.size());
    assertEquals(true, services.containsKey(DemoService.class.getTypeName()));
  }

  interface DemoService {
    Future<String> hello(String n);
  }

  interface OtherService {
    Future<String> ping();
  }

  static final class DemoServiceImpl implements DemoService {
    @Override
    public Future<String> hello(String n) {
      return Future.succeededFuture(n);
    }
  }

  static final class OtherServiceImpl implements OtherService {
    @Override
    public Future<String> ping() {
      return Future.succeededFuture("pong");
    }
  }
}
