package com.github.rpc0.client;

import com.google.common.base.Strings;
import com.github.rpc0.invoke.InvokeResult;
import com.github.rpc0.invoke.InvokeSpec;
import com.github.rpc0.invoke.ParameterArray;
import com.github.rpc0.invoke.ResultCode;
import com.github.rpc0.reflection.ReflectionUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.VertxInternal;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * @author fishzhao
 * @since 2021-12-17
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class ProxyStubInvocationHandler implements InvocationHandler {

  private static final AtomicLong ID_GENERATOR = new AtomicLong(0);

  private final VertxInternal vertxInternal;
  private final Supplier<Future<ProxyStub>> proxyStubSupplier;

  private void checkMethodReturnType(Method method) {
    if (method.getReturnType() != Future.class) {
      throw new UnsupportedOperationException(
              Strings.lenientFormat(
                      "The return raw type of method \"%s\" must be : %s",
                      method,
                      Future.class
              )
      );
    }
  }

  @SuppressWarnings("SuspiciousInvocationHandlerImplementation")
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) {
    checkMethodReturnType(method);
    Class<?> actualReturnType = ReflectionUtil.getFutureResultType(method.getGenericReturnType());
    InvokeSpec invokeSpec = buildInvokeSpec(
            method,
            actualReturnType,
            args
    );
    Promise<Object> promise = vertxInternal.promise();
    Future<ProxyStub> proxyStubFuture = proxyStubSupplier.get();
    // optimize for performance
    if (proxyStubFuture.succeeded()) {
      proxyStubFuture.result()
              .call(invokeSpec)
              .onComplete(result ->
                      handleResult(result, promise, actualReturnType));
    } else {
      proxyStubFuture.onComplete(stubResult -> {
        if (stubResult.succeeded()) {
          proxyStubFuture.result()
                  .call(invokeSpec)
                  .onComplete(result ->
                          handleResult(result, promise, actualReturnType));
        } else {
          promise.tryFail(stubResult.cause());
        }
      });
    }
    return promise.future();
  }

  private void handleResult(AsyncResult<InvokeResult> result,
                            Promise<Object> promise,
                            Class<?> actualReturnType) {
    if (result.succeeded()) {
      InvokeResult invokeResult = result.result();
      if (invokeResult.getCode() == ResultCode.OK) {
        Object o = invokeResult.getResult();
        if (o == null || actualReturnType.isInstance(o)) {
          promise.complete(o);
        } else {
          promise.tryFail(Strings.lenientFormat("Mismatch result type, required: %s, but found: %s",
                  actualReturnType,
                  o.getClass()));
        }
      } else {
        promise.tryFail(invokeResult.getErrorMessage());
      }
    } else {
      promise.tryFail(result.cause());
    }
  }


  private InvokeSpec buildInvokeSpec(Method method,
                                     Class<?> actualReturnType,
                                     Object[] args) {
    ParameterArray parameters = ParameterArray.create(args);
    return new InvokeSpec(
            ID_GENERATOR.incrementAndGet(),
            System.currentTimeMillis(),
            method.getDeclaringClass().getTypeName(),
            method.getName(),
            MethodType.methodType(actualReturnType, method.getParameterTypes()),
            parameters
    );
  }
}
