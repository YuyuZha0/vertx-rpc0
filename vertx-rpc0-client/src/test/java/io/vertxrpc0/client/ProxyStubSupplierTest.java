package io.vertxrpc0.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertxrpc0.kryo.KryoFactory;
import io.vertxrpc0.transport.KryoMessageTransport;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class ProxyStubSupplierTest {

  private static KryoMessageTransport transport() {
    return new KryoMessageTransport(new KryoFactory());
  }

  private static ContextInternal contextOf(Vertx vertx) {
    return (ContextInternal) vertx.getOrCreateContext();
  }

  // ---------------------------------------------------------------------------
  // Sync construction-validation tests (no Vertx event loop work).
  // ---------------------------------------------------------------------------

  private static int unreachablePort() {
    // 1 is a privileged + closed port on every loopback we'll see in CI/dev.
    return 1;
  }

  @Test
  public void constructorRejectsNonPositiveInitialBackoff(Vertx vertx) {
    ContextInternal context = contextOf(vertx);
    KryoMessageTransport transport = transport();
    assertThrows(IllegalArgumentException.class, () -> new ProxyStubSupplier(
            context, vertx.createNetClient(), transport, Duration.ofSeconds(1), "localhost", 1,
            Duration.ZERO, Duration.ofSeconds(1)));
    assertThrows(IllegalArgumentException.class, () -> new ProxyStubSupplier(
            context, vertx.createNetClient(), transport, Duration.ofSeconds(1), "localhost", 1,
            Duration.ofMillis(-1), Duration.ofSeconds(1)));
  }

  @Test
  public void constructorRejectsMaxBackoffSmallerThanInitial(Vertx vertx) {
    assertThrows(IllegalArgumentException.class, () -> new ProxyStubSupplier(
            contextOf(vertx), vertx.createNetClient(), transport(),
            Duration.ofSeconds(1), "localhost", 1,
            Duration.ofSeconds(5), Duration.ofSeconds(1)));
  }

  // ---------------------------------------------------------------------------
  // Async tests driven through VertxTestContext.
  // ---------------------------------------------------------------------------

  /** Pure backoff-math test — reflection on a private method, no event loop interaction. */
  @Test
  public void backoffGrowsExponentiallyThenCaps(Vertx vertx) throws Exception {
    ProxyStubSupplier supplier = new ProxyStubSupplier(
            contextOf(vertx), vertx.createNetClient(), transport(),
            Duration.ofSeconds(1), "127.0.0.1", 1,
            Duration.ofMillis(100), Duration.ofSeconds(2));

    java.lang.reflect.Method m = ProxyStubSupplier.class.getDeclaredMethod(
            "computeBackoffMillis", int.class);
    m.setAccessible(true);
    assertEquals(100L, m.invoke(supplier, 1));
    assertEquals(200L, m.invoke(supplier, 2));
    assertEquals(400L, m.invoke(supplier, 3));
    assertEquals(800L, m.invoke(supplier, 4));
    assertEquals(1600L, m.invoke(supplier, 5));
    assertEquals(2000L, m.invoke(supplier, 6));   // capped
    assertEquals(2000L, m.invoke(supplier, 100)); // still capped
    assertEquals(2000L, m.invoke(supplier, 64));  // no overflow at large shift counts
  }

  @Test
  public void concurrentGetsShareTheSameInflightConnect(Vertx vertx, VertxTestContext ctx) {
    ProxyStubSupplier supplier = new ProxyStubSupplier(
            contextOf(vertx),
            vertx.createNetClient(new NetClientOptions().setConnectTimeout(200)),
            transport(), Duration.ofSeconds(1), "127.0.0.1", unreachablePort(),
            Duration.ofMillis(200), Duration.ofSeconds(1));

    Future<ProxyStub> f1 = supplier.get();
    Future<ProxyStub> f2 = supplier.get();
    Future<ProxyStub> f3 = supplier.get();

    // Wait for ALL three to complete (regardless of outcome) before asserting:
    // f1's own onComplete handler fires before the supplier's internal forwarders to
    // f2/f3, so asserting inside f1.onComplete would race.
    io.vertx.core.CompositeFuture.join(f1, f2, f3).onComplete(ar -> ctx.verify(() -> {
      assertTrue(f1.failed());
      assertTrue(f2.failed());
      assertTrue(f3.failed());
      ctx.completeNow();
    }));
  }

  @Test
  public void failedConnectsAreCachedDuringBackoffWindow(Vertx vertx, VertxTestContext ctx) {
    ProxyStubSupplier supplier = new ProxyStubSupplier(
            contextOf(vertx),
            vertx.createNetClient(new NetClientOptions().setConnectTimeout(200)),
            transport(), Duration.ofSeconds(1), "127.0.0.1", unreachablePort(),
            Duration.ofSeconds(5), Duration.ofSeconds(5));

    Future<ProxyStub> first = supplier.get();
    first.onComplete(ctx.failing(cause -> ctx.verify(() -> {
      // Within the 5s backoff window, .get() must return the same cached failed future.
      Future<ProxyStub> second = supplier.get();
      assertSame(first, second,
              "concurrent .get() within backoff must return the cached future");
      assertTrue(second.failed());
      ctx.completeNow();
    })));
  }

  @Test
  public void successfulConnectIsCachedAndReused(Vertx vertx, VertxTestContext ctx) {
    NetServer accept = vertx.createNetServer(
            new NetServerOptions().setHost("127.0.0.1").setPort(0));
    accept.connectHandler(sock -> { /* hold the connection open */ });

    accept.listen().onComplete(ctx.succeeding(listening -> {
      ProxyStubSupplier supplier = new ProxyStubSupplier(
              contextOf(vertx), vertx.createNetClient(), transport(),
              Duration.ofSeconds(1), "127.0.0.1", listening.actualPort());

      supplier.get().onComplete(ctx.succeeding(stub1 -> ctx.verify(() -> {
        assertNotNull(stub1);
        supplier.get().onComplete(ctx.succeeding(stub2 -> ctx.verify(() -> {
          assertSame(stub1, stub2);
          ctx.completeNow();
        })));
      })));
    }));
  }

  @Test
  public void getAfterCloseFailsImmediately(Vertx vertx, VertxTestContext ctx) {
    ProxyStubSupplier supplier = new ProxyStubSupplier(
            contextOf(vertx), vertx.createNetClient(), transport(),
            Duration.ofSeconds(1), "127.0.0.1", 1);
    Promise<Void> closed = Promise.promise();
    supplier.close(closed);
    closed.future().onComplete(ctx.succeeding(v -> ctx.verify(() -> {
      Future<ProxyStub> f = supplier.get();
      assertTrue(f.failed());
      assertEquals("Connection unavailable for already closed!", f.cause().getMessage());
      ctx.completeNow();
    })));
  }

  @Test
  public void closeTwiceFailsTheSecondCall(Vertx vertx, VertxTestContext ctx) {
    ProxyStubSupplier supplier = new ProxyStubSupplier(
            contextOf(vertx), vertx.createNetClient(), transport(),
            Duration.ofSeconds(1), "127.0.0.1", 1);
    Promise<Void> first = Promise.promise();
    supplier.close(first);
    first.future().onComplete(ctx.succeeding(v -> {
      Promise<Void> second = Promise.promise();
      supplier.close(second);
      ctx.verify(() -> assertTrue(second.future().failed()));
      ctx.completeNow();
    }));
  }
}
