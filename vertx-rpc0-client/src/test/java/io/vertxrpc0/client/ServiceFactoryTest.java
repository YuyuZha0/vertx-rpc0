package io.vertxrpc0.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableSet;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

@ExtendWith(VertxExtension.class)
public class ServiceFactoryTest {

  private ProxyStubSupplier supplier;

  @BeforeEach
  public void setUp() {
    supplier = Mockito.mock(ProxyStubSupplier.class);
  }

  @Test
  public void createForRegisteredInterfaceReturnsProxy(Vertx vertx) {
    ServiceFactory factory = new ServiceFactory(ImmutableSet.of(ServiceA.class), vertx, supplier);
    ServiceA proxy = factory.create(ServiceA.class);
    assertNotNull(proxy);
    assertTrue(proxy instanceof ServiceA);
  }

  @Test
  public void createForUnregisteredInterfaceRejected(Vertx vertx) {
    ServiceFactory factory = new ServiceFactory(ImmutableSet.of(ServiceA.class), vertx, supplier);
    assertThrows(IllegalArgumentException.class, () -> factory.create(ServiceB.class));
  }

  @Test
  public void getOrCreateCachesProxyInstance(Vertx vertx) {
    ServiceFactory factory = new ServiceFactory(ImmutableSet.of(ServiceA.class), vertx, supplier);
    ServiceA first = factory.getOrCreate(ServiceA.class);
    ServiceA second = factory.getOrCreate(ServiceA.class);
    assertSame(first, second);
  }

  @Test
  public void getOrCreateIsThreadSafe(Vertx vertx, VertxTestContext ctx) {
    ServiceFactory factory = new ServiceFactory(ImmutableSet.of(ServiceA.class), vertx, supplier);
    int threads = 32;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    // CountDownLatch coordinates a simultaneous start across all workers; it's
    // a synchronization primitive between worker threads, NOT a block on the
    // test thread. The test thread returns immediately and waits via
    // VertxTestContext + CompositeFuture.all.
    CountDownLatch start = new CountDownLatch(1);
    List<Future<ServiceA>> results = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      Promise<ServiceA> p = Promise.promise();
      results.add(p.future());
      pool.submit(() -> {
        try {
          start.await();
          p.tryComplete(factory.getOrCreate(ServiceA.class));
        } catch (Throwable t) {
          p.tryFail(t);
        }
      });
    }
    start.countDown();
    pool.shutdown();

    CompositeFuture.all(new ArrayList<>(results)).onComplete(ctx.succeeding(cf -> ctx.verify(() -> {
      ServiceA first = cf.resultAt(0);
      for (int i = 1; i < threads; i++) {
        assertSame(first, cf.resultAt(i),
                "all threads should see the same cached proxy instance");
      }
      ctx.completeNow();
    })));
  }

  @Test
  public void closeClearsCacheAndPropagatesToSupplier(Vertx vertx) {
    Set<Class<?>> registry = new HashSet<>();
    registry.add(ServiceA.class);
    ServiceFactory factory = new ServiceFactory(ImmutableSet.copyOf(registry), vertx, supplier);
    factory.getOrCreate(ServiceA.class);
    Promise<Void> p = Promise.promise();
    factory.close(p);
    Mockito.verify(supplier).close(p);
  }

  interface ServiceA {}

  interface ServiceB {}
}
