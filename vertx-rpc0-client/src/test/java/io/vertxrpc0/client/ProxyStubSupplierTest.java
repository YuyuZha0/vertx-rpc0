package io.vertxrpc0.client;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertxrpc0.kryo.KryoFactory;
import io.vertxrpc0.transport.KryoMessageTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
public class ProxyStubSupplierTest {

  private Vertx vertx;
  private ContextInternal context;
  private KryoMessageTransport transport;

  @BeforeEach
  public void setUp() {
    vertx = Vertx.vertx();
    context = (ContextInternal) vertx.getOrCreateContext();
    transport = new KryoMessageTransport(new KryoFactory());
  }

  @AfterEach
  public void tearDown() throws Exception {
    vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
  }

  private static <T> T await(Future<T> f) throws Exception {
    return f.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
  }

  @Test
  public void constructorRejectsNonPositiveInitialBackoff() {
    assertThrows(IllegalArgumentException.class, () -> new ProxyStubSupplier(
            context, vertx.createNetClient(), transport, Duration.ofSeconds(1), "localhost", 1,
            Duration.ZERO, Duration.ofSeconds(1)));
    assertThrows(IllegalArgumentException.class, () -> new ProxyStubSupplier(
            context, vertx.createNetClient(), transport, Duration.ofSeconds(1), "localhost", 1,
            Duration.ofMillis(-1), Duration.ofSeconds(1)));
  }

  @Test
  public void constructorRejectsMaxBackoffSmallerThanInitial() {
    assertThrows(IllegalArgumentException.class, () -> new ProxyStubSupplier(
            context, vertx.createNetClient(), transport, Duration.ofSeconds(1), "localhost", 1,
            Duration.ofSeconds(5), Duration.ofSeconds(1)));
  }

  @Test
  public void concurrentGetsShareTheSameInflightConnect() throws Exception {
    // No server listening — connects will fail. We just need the same failed future shared.
    int unreachablePort = unreachablePort();
    ProxyStubSupplier supplier = new ProxyStubSupplier(
            context, vertx.createNetClient(new NetClientOptions().setConnectTimeout(200)),
            transport, Duration.ofSeconds(1), "127.0.0.1", unreachablePort,
            Duration.ofMillis(200), Duration.ofSeconds(1));

    Future<ProxyStub> f1 = supplier.get();
    Future<ProxyStub> f2 = supplier.get();
    Future<ProxyStub> f3 = supplier.get();

    // All three calls return promises that resolve to the same underlying connect outcome.
    try {
      await(f1);
      fail("expected failure");
    } catch (ExecutionException expected) {
      // ok
    }
    // f2 and f3 must complete with the same failure mode.
    assertTrue(f2.failed());
    assertTrue(f3.failed());

    closeSupplier(supplier);
  }

  @Test
  public void failedConnectsAreCachedDuringBackoffWindow() throws Exception {
    int unreachablePort = unreachablePort();
    ProxyStubSupplier supplier = new ProxyStubSupplier(
            context, vertx.createNetClient(new NetClientOptions().setConnectTimeout(200)),
            transport, Duration.ofSeconds(1), "127.0.0.1", unreachablePort,
            Duration.ofSeconds(5), Duration.ofSeconds(5));

    Future<ProxyStub> first = supplier.get();
    try {
      await(first);
      fail("expected first connect to fail");
    } catch (ExecutionException expected) {
      // ok
    }

    // Within the 5s backoff window, .get() should return the same cached failed future
    // — no new connect attempt.
    Future<ProxyStub> second = supplier.get();
    assertSame(first, second, "concurrent .get() within backoff must return the cached future");
    assertTrue(second.failed());

    closeSupplier(supplier);
  }

  @Test
  public void successfulConnectIsCachedAndReused() throws Exception {
    NetServer accept = vertx.createNetServer(new NetServerOptions().setHost("127.0.0.1").setPort(0));
    accept.connectHandler(sock -> { /* hold the connection open */ });
    NetServer listening = await(accept.listen());

    ProxyStubSupplier supplier = new ProxyStubSupplier(
            context, vertx.createNetClient(), transport,
            Duration.ofSeconds(1), "127.0.0.1", listening.actualPort());

    ProxyStub stub1 = await(supplier.get());
    ProxyStub stub2 = await(supplier.get());
    assertNotNull(stub1);
    assertSame(stub1, stub2);

    closeSupplier(supplier);
    listening.close().toCompletionStage().toCompletableFuture().get(2, TimeUnit.SECONDS);
  }

  @Test
  public void getAfterCloseFailsImmediately() throws Exception {
    ProxyStubSupplier supplier = new ProxyStubSupplier(
            context, vertx.createNetClient(), transport,
            Duration.ofSeconds(1), "127.0.0.1", 1);
    closeSupplier(supplier);

    Future<ProxyStub> f = supplier.get();
    assertTrue(f.failed());
    assertEquals("Connection unavailable for already closed!", f.cause().getMessage());
  }

  @Test
  public void closeTwiceFailsTheSecondCall() throws Exception {
    ProxyStubSupplier supplier = new ProxyStubSupplier(
            context, vertx.createNetClient(), transport,
            Duration.ofSeconds(1), "127.0.0.1", 1);
    Promise<Void> first = Promise.promise();
    supplier.close(first);
    await(first.future());

    Promise<Void> second = Promise.promise();
    supplier.close(second);
    assertTrue(second.future().failed());
  }

  @Test
  public void backoffGrowsExponentiallyThenCaps() throws Exception {
    // Indirectly verify via the package-private computeBackoffMillis through reflection.
    ProxyStubSupplier supplier = new ProxyStubSupplier(
            context, vertx.createNetClient(), transport,
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

    closeSupplier(supplier);
  }

  private static int unreachablePort() {
    // 1 is a privileged + closed port on every loopback we'll see in CI/dev.
    return 1;
  }

  private static void closeSupplier(ProxyStubSupplier supplier) throws Exception {
    Promise<Void> p = Promise.promise();
    supplier.close(p);
    CompletableFuture<Void> cf = p.future().toCompletionStage().toCompletableFuture();
    try {
      cf.get(2, TimeUnit.SECONDS);
    } catch (ExecutionException ignored) {
      // Even a failed close is fine for cleanup.
    }
  }
}
