package io.vertxrpc0.client;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertxrpc0.invoke.InvokeResult;
import io.vertxrpc0.invoke.InvokeSpec;
import io.vertxrpc0.invoke.ResultCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class ProxyStubInvocationHandlerTest {

  interface AsyncService {
    Future<String> hello(String name);

    Future<Integer> count();
  }

  interface SyncService {
    String hello(String name);
  }

  private static Method method(Class<?> iface, String name) throws Exception {
    for (Method m : iface.getDeclaredMethods()) {
      if (m.getName().equals(name)) return m;
    }
    throw new NoSuchMethodException(name);
  }

  @SuppressWarnings("unchecked")
  private static Supplier<Future<ProxyStub>> mockSupplier() {
    return Mockito.mock(Supplier.class);
  }

  @Test
  public void rejectsNonFutureReturnType(Vertx vertx) throws Exception {
    ProxyStubInvocationHandler handler =
            new ProxyStubInvocationHandler((VertxInternal) vertx, mockSupplier());
    Method m = method(SyncService.class, "hello");
    assertThrows(UnsupportedOperationException.class,
            () -> handler.invoke(null, m, new Object[]{"x"}));
  }

  @Test
  public void supplierFailurePropagatesToReturnedFuture(Vertx vertx, VertxTestContext ctx) throws Exception {
    RuntimeException boom = new RuntimeException("no-stub");
    Supplier<Future<ProxyStub>> supplier = mockSupplier();
    Mockito.when(supplier.get()).thenReturn(Future.failedFuture(boom));

    ProxyStubInvocationHandler handler =
            new ProxyStubInvocationHandler((VertxInternal) vertx, supplier);
    Future<?> ret = (Future<?>) handler.invoke(
            null, method(AsyncService.class, "hello"), new Object[]{"world"});

    ret.onComplete(ctx.failing(cause -> ctx.verify(() -> {
      assertEquals(boom, cause);
      ctx.completeNow();
    })));
  }

  @Test
  public void okResultIsResolved(Vertx vertx, VertxTestContext ctx) throws Exception {
    ProxyStub stub = Mockito.mock(ProxyStub.class);
    Mockito.when(stub.call(Mockito.any(InvokeSpec.class)))
            .thenAnswer(inv -> Future.succeededFuture(
                    new InvokeResult(1, 0L, ResultCode.OK, "", "hi-world")));

    Supplier<Future<ProxyStub>> supplier = mockSupplier();
    Mockito.when(supplier.get()).thenReturn(Future.succeededFuture(stub));

    ProxyStubInvocationHandler handler =
            new ProxyStubInvocationHandler((VertxInternal) vertx, supplier);
    Future<?> ret = (Future<?>) handler.invoke(
            null, method(AsyncService.class, "hello"), new Object[]{"world"});

    ret.onComplete(ctx.succeeding(result -> ctx.verify(() -> {
      assertEquals("hi-world", result);
      ctx.completeNow();
    })));
  }

  @Test
  public void errorCodePropagatesAsFailure(Vertx vertx, VertxTestContext ctx) throws Exception {
    ProxyStub stub = Mockito.mock(ProxyStub.class);
    Mockito.when(stub.call(Mockito.any(InvokeSpec.class)))
            .thenAnswer(inv -> Future.succeededFuture(
                    new InvokeResult(1, 0L, ResultCode.INVOCATION_ERROR, "boom!", null)));

    Supplier<Future<ProxyStub>> supplier = mockSupplier();
    Mockito.when(supplier.get()).thenReturn(Future.succeededFuture(stub));

    ProxyStubInvocationHandler handler =
            new ProxyStubInvocationHandler((VertxInternal) vertx, supplier);
    Future<?> ret = (Future<?>) handler.invoke(
            null, method(AsyncService.class, "hello"), new Object[]{"world"});

    ret.onComplete(ctx.failing(cause -> ctx.verify(() -> {
      assertEquals("boom!", cause.getMessage());
      ctx.completeNow();
    })));
  }

  @Test
  public void mismatchResultTypeIsRejected(Vertx vertx, VertxTestContext ctx) throws Exception {
    ProxyStub stub = Mockito.mock(ProxyStub.class);
    Mockito.when(stub.call(Mockito.any(InvokeSpec.class)))
            .thenAnswer(inv -> Future.succeededFuture(
                    new InvokeResult(1, 0L, ResultCode.OK, "", 42L)));

    Supplier<Future<ProxyStub>> supplier = mockSupplier();
    Mockito.when(supplier.get()).thenReturn(Future.succeededFuture(stub));

    ProxyStubInvocationHandler handler =
            new ProxyStubInvocationHandler((VertxInternal) vertx, supplier);
    Future<?> ret = (Future<?>) handler.invoke(
            null, method(AsyncService.class, "hello"), new Object[]{"world"});

    ret.onComplete(ctx.failing(cause -> ctx.verify(() -> {
      assertTrue(cause.getMessage().contains("Mismatch result type"));
      ctx.completeNow();
    })));
  }

  @Test
  public void invokeSendsSpecToProxyStub(Vertx vertx, VertxTestContext ctx) throws Exception {
    ProxyStub stub = Mockito.mock(ProxyStub.class);
    Mockito.when(stub.call(Mockito.any(InvokeSpec.class)))
            .thenAnswer(inv -> Future.succeededFuture(
                    new InvokeResult(1, 0L, ResultCode.OK, "", 7)));

    Supplier<Future<ProxyStub>> supplier = mockSupplier();
    Mockito.when(supplier.get()).thenReturn(Future.succeededFuture(stub));

    ProxyStubInvocationHandler handler =
            new ProxyStubInvocationHandler((VertxInternal) vertx, supplier);
    Future<?> ret = (Future<?>) handler.invoke(
            null, method(AsyncService.class, "count"), new Object[]{});

    ret.onComplete(ctx.succeeding(result -> ctx.verify(() -> {
      ArgumentCaptor<InvokeSpec> captor = ArgumentCaptor.forClass(InvokeSpec.class);
      Mockito.verify(stub).call(captor.capture());
      InvokeSpec spec = captor.getValue();
      assertEquals("count", spec.getMethodName());
      assertEquals(AsyncService.class.getTypeName(), spec.getCallSiteClassName());
      assertEquals(0, spec.getParameters().size());
      ctx.completeNow();
    })));
  }

  @Test
  public void objectMethodsAreHandledLocallyNotRpc(Vertx vertx) throws Exception {
    Supplier<Future<ProxyStub>> supplier = mockSupplier();
    ProxyStubInvocationHandler handler =
            new ProxyStubInvocationHandler((VertxInternal) vertx, supplier);

    Object proxy = new Object();
    Object hashCode = handler.invoke(proxy, Object.class.getMethod("hashCode"), null);
    assertTrue(hashCode instanceof Integer);

    Object eqSelf = handler.invoke(
            proxy, Object.class.getMethod("equals", Object.class), new Object[]{proxy});
    assertEquals(Boolean.TRUE, eqSelf);

    Mockito.verifyNoInteractions(supplier);
  }

  @Test
  public void nullResultIsAllowed(Vertx vertx, VertxTestContext ctx) throws Exception {
    ProxyStub stub = Mockito.mock(ProxyStub.class);
    Mockito.when(stub.call(Mockito.any(InvokeSpec.class)))
            .thenAnswer(inv -> Future.succeededFuture(
                    new InvokeResult(1, 0L, ResultCode.OK, "", null)));

    Supplier<Future<ProxyStub>> supplier = mockSupplier();
    Mockito.when(supplier.get()).thenReturn(Future.succeededFuture(stub));

    ProxyStubInvocationHandler handler =
            new ProxyStubInvocationHandler((VertxInternal) vertx, supplier);
    Future<?> ret = (Future<?>) handler.invoke(
            null, method(AsyncService.class, "hello"), new Object[]{"x"});

    ret.onComplete(ctx.succeeding(result -> ctx.verify(() -> {
      assertNull(result);
      ctx.completeNow();
    })));
  }
}
