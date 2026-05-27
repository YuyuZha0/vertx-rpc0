package io.vertxrpc0.client;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertxrpc0.invoke.InvokeResult;
import io.vertxrpc0.invoke.InvokeSpec;
import io.vertxrpc0.invoke.ResultCode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProxyStubInvocationHandlerTest {

  interface AsyncService {
    Future<String> hello(String name);

    Future<Integer> count();
  }

  interface SyncService {
    String hello(String name);
  }

  private static Vertx vertx;

  @BeforeAll
  public static void setUp() {
    vertx = Vertx.vertx();
  }

  @AfterAll
  public static void tearDown() throws Exception {
    vertx.close().toCompletionStage().toCompletableFuture().get();
  }

  @SuppressWarnings("unchecked")
  private static Method method(Class<?> iface, String name) throws Exception {
    for (Method m : iface.getDeclaredMethods()) {
      if (m.getName().equals(name)) return m;
    }
    throw new NoSuchMethodException(name);
  }

  @Test
  public void rejectsNonFutureReturnType() throws Exception {
    @SuppressWarnings("unchecked")
    java.util.function.Supplier<Future<ProxyStub>> stubSupplier = Mockito.mock(java.util.function.Supplier.class);
    ProxyStubInvocationHandler handler = new ProxyStubInvocationHandler((VertxInternal) vertx, stubSupplier);
    Method m = method(SyncService.class, "hello");
    assertThrows(UnsupportedOperationException.class, () -> handler.invoke(null, m, new Object[]{"x"}));
  }

  @Test
  public void supplierFailurePropagatesToReturnedFuture() throws Exception {
    @SuppressWarnings("unchecked")
    java.util.function.Supplier<Future<ProxyStub>> stubSupplier = Mockito.mock(java.util.function.Supplier.class);
    RuntimeException boom = new RuntimeException("no-stub");
    Mockito.when(stubSupplier.get()).thenReturn(Future.failedFuture(boom));

    ProxyStubInvocationHandler handler = new ProxyStubInvocationHandler((VertxInternal) vertx, stubSupplier);
    Future<?> ret = (Future<?>) handler.invoke(null, method(AsyncService.class, "hello"), new Object[]{"world"});

    assertTrue(ret.failed());
    assertEquals(boom, ret.cause());
  }

  @Test
  public void okResultIsResolved() throws Exception {
    ProxyStub stub = Mockito.mock(ProxyStub.class);
    Mockito.when(stub.call(Mockito.any(InvokeSpec.class)))
            .thenAnswer(inv -> Future.succeededFuture(new InvokeResult(1, 0L, ResultCode.OK, "", "hi-world")));

    @SuppressWarnings("unchecked")
    java.util.function.Supplier<Future<ProxyStub>> stubSupplier = Mockito.mock(java.util.function.Supplier.class);
    Mockito.when(stubSupplier.get()).thenReturn(Future.succeededFuture(stub));

    ProxyStubInvocationHandler handler = new ProxyStubInvocationHandler((VertxInternal) vertx, stubSupplier);
    Future<?> ret = (Future<?>) handler.invoke(null, method(AsyncService.class, "hello"), new Object[]{"world"});

    assertTrue(ret.succeeded());
    assertEquals("hi-world", ret.result());
  }

  @Test
  public void errorCodePropagatesAsFailure() throws Exception {
    ProxyStub stub = Mockito.mock(ProxyStub.class);
    Mockito.when(stub.call(Mockito.any(InvokeSpec.class)))
            .thenAnswer(inv -> Future.succeededFuture(
                    new InvokeResult(1, 0L, ResultCode.INVOCATION_ERROR, "boom!", null)));

    @SuppressWarnings("unchecked")
    java.util.function.Supplier<Future<ProxyStub>> stubSupplier = Mockito.mock(java.util.function.Supplier.class);
    Mockito.when(stubSupplier.get()).thenReturn(Future.succeededFuture(stub));

    ProxyStubInvocationHandler handler = new ProxyStubInvocationHandler((VertxInternal) vertx, stubSupplier);
    Future<?> ret = (Future<?>) handler.invoke(null, method(AsyncService.class, "hello"), new Object[]{"world"});

    assertTrue(ret.failed());
    assertEquals("boom!", ret.cause().getMessage());
  }

  @Test
  public void mismatchResultTypeIsRejected() throws Exception {
    ProxyStub stub = Mockito.mock(ProxyStub.class);
    Mockito.when(stub.call(Mockito.any(InvokeSpec.class)))
            .thenAnswer(inv -> Future.succeededFuture(
                    new InvokeResult(1, 0L, ResultCode.OK, "", 42L)));

    @SuppressWarnings("unchecked")
    java.util.function.Supplier<Future<ProxyStub>> stubSupplier = Mockito.mock(java.util.function.Supplier.class);
    Mockito.when(stubSupplier.get()).thenReturn(Future.succeededFuture(stub));

    ProxyStubInvocationHandler handler = new ProxyStubInvocationHandler((VertxInternal) vertx, stubSupplier);
    Future<?> ret = (Future<?>) handler.invoke(null, method(AsyncService.class, "hello"), new Object[]{"world"});

    assertTrue(ret.failed());
    assertTrue(ret.cause().getMessage().contains("Mismatch result type"));
  }

  @Test
  public void invokeSendsSpecToProxyStub() throws Exception {
    ProxyStub stub = Mockito.mock(ProxyStub.class);
    Mockito.when(stub.call(Mockito.any(InvokeSpec.class)))
            .thenAnswer(inv -> Future.succeededFuture(
                    new InvokeResult(1, 0L, ResultCode.OK, "", 7)));

    @SuppressWarnings("unchecked")
    java.util.function.Supplier<Future<ProxyStub>> stubSupplier = Mockito.mock(java.util.function.Supplier.class);
    Mockito.when(stubSupplier.get()).thenReturn(Future.succeededFuture(stub));

    ProxyStubInvocationHandler handler = new ProxyStubInvocationHandler((VertxInternal) vertx, stubSupplier);
    handler.invoke(null, method(AsyncService.class, "count"), new Object[]{});

    ArgumentCaptor<InvokeSpec> captor = ArgumentCaptor.forClass(InvokeSpec.class);
    Mockito.verify(stub).call(captor.capture());
    InvokeSpec spec = captor.getValue();
    assertEquals("count", spec.getMethodName());
    assertEquals(AsyncService.class.getTypeName(), spec.getCallSiteClassName());
    assertEquals(0, spec.getParameters().size());
  }

  @Test
  public void objectMethodsAreHandledLocallyNotRpc() throws Exception {
    @SuppressWarnings("unchecked")
    java.util.function.Supplier<Future<ProxyStub>> stubSupplier = Mockito.mock(java.util.function.Supplier.class);
    ProxyStubInvocationHandler handler = new ProxyStubInvocationHandler((VertxInternal) vertx, stubSupplier);

    // hashCode() must not throw and must not invoke the supplier
    Object stubProxy = new Object();
    Object hashCode = handler.invoke(stubProxy, Object.class.getMethod("hashCode"), null);
    assertTrue(hashCode instanceof Integer);

    // equals() must compare by identity, also without invoking the supplier
    Object eqSelf = handler.invoke(stubProxy, Object.class.getMethod("equals", Object.class), new Object[]{stubProxy});
    assertEquals(Boolean.TRUE, eqSelf);

    Mockito.verifyNoInteractions(stubSupplier);
  }

  @Test
  public void nullResultIsAllowed() throws Exception {
    ProxyStub stub = Mockito.mock(ProxyStub.class);
    Mockito.when(stub.call(Mockito.any(InvokeSpec.class)))
            .thenAnswer(inv -> Future.succeededFuture(new InvokeResult(1, 0L, ResultCode.OK, "", null)));

    @SuppressWarnings("unchecked")
    java.util.function.Supplier<Future<ProxyStub>> stubSupplier = Mockito.mock(java.util.function.Supplier.class);
    Mockito.when(stubSupplier.get()).thenReturn(Future.succeededFuture(stub));

    ProxyStubInvocationHandler handler = new ProxyStubInvocationHandler((VertxInternal) vertx, stubSupplier);
    Future<?> ret = (Future<?>) handler.invoke(null, method(AsyncService.class, "hello"), new Object[]{"x"});

    assertTrue(ret.succeeded());
    assertNull(ret.result());
  }
}
