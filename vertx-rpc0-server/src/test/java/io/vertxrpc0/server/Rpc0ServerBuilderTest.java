package io.vertxrpc0.server;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServerOptions;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(VertxExtension.class)
public class Rpc0ServerBuilderTest {

  interface DemoService {
    Future<String> hello(String n);
  }

  static final class DemoServiceImpl implements DemoService {
    @Override
    public Future<String> hello(String n) {
      return Future.succeededFuture(n);
    }
  }

  @Test
  public void buildWithoutAnyBindingIsRejected(Vertx vertx) {
    Rpc0ServerBuilder builder = new Rpc0ServerBuilder(vertx, new NetServerOptions());
    assertThrows(IllegalArgumentException.class, builder::build);
  }

  @Test
  public void addBindingRejectsConcreteClass(Vertx vertx) {
    Rpc0ServerBuilder builder = new Rpc0ServerBuilder(vertx, new NetServerOptions());
    assertThrows(IllegalArgumentException.class,
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
    assertThrows(NullPointerException.class, () -> new Rpc0ServerBuilder(null, new NetServerOptions()));
  }

  @Test
  public void setKeepAliveDurationRejectsNull(Vertx vertx) {
    Rpc0ServerBuilder builder = new Rpc0ServerBuilder(vertx, new NetServerOptions());
    assertThrows(NullPointerException.class, () -> builder.setKeepAliveDuration(null));
  }
}
