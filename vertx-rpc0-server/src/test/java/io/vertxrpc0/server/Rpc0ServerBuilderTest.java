package io.vertxrpc0.server;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServerOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
  public void buildWithoutAnyBindingIsRejected() {
    Rpc0ServerBuilder builder = new Rpc0ServerBuilder(vertx, new NetServerOptions());
    assertThrows(IllegalArgumentException.class, builder::build);
  }

  @Test
  public void addBindingRejectsConcreteClass() {
    Rpc0ServerBuilder builder = new Rpc0ServerBuilder(vertx, new NetServerOptions());
    assertThrows(IllegalArgumentException.class,
            () -> builder.addBinding((Class) DemoServiceImpl.class, new DemoServiceImpl()));
  }

  @Test
  public void buildProducesUsableServer() {
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
  public void setKeepAliveDurationRejectsNull() {
    Rpc0ServerBuilder builder = new Rpc0ServerBuilder(vertx, new NetServerOptions());
    assertThrows(NullPointerException.class, () -> builder.setKeepAliveDuration(null));
  }
}
