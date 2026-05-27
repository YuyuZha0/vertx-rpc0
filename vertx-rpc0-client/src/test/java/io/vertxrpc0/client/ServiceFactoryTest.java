package io.vertxrpc0.client;

import com.google.common.collect.ImmutableSet;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class ServiceFactoryTest {

  interface ServiceA {}
  interface ServiceB {}

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
  public void getOrCreateIsThreadSafe(Vertx vertx) throws Exception {
    ServiceFactory factory = new ServiceFactory(ImmutableSet.of(ServiceA.class), vertx, supplier);
    int threads = 32;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    java.util.List<java.util.concurrent.Future<ServiceA>> futures = new java.util.ArrayList<>();
    try {
      for (int i = 0; i < threads; i++) {
        futures.add(pool.submit(() -> {
          start.await();
          return factory.getOrCreate(ServiceA.class);
        }));
      }
      start.countDown();
      pool.shutdown();
      ServiceA first = futures.get(0).get(5, TimeUnit.SECONDS);
      for (java.util.concurrent.Future<ServiceA> f : futures) {
        assertSame(first, f.get(5, TimeUnit.SECONDS),
                "all threads should see the same cached proxy instance");
      }
      assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  public void closeClearsCacheAndPropagatesToSupplier(Vertx vertx) throws Exception {
    Set<Class<?>> registry = new HashSet<>();
    registry.add(ServiceA.class);
    ServiceFactory factory = new ServiceFactory(ImmutableSet.copyOf(registry), vertx, supplier);
    factory.getOrCreate(ServiceA.class);
    io.vertx.core.Promise<Void> p = io.vertx.core.Promise.promise();
    factory.close(p);
    Mockito.verify(supplier).close(p);
  }
}
