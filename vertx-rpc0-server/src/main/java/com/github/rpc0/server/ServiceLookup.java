package com.github.rpc0.server;

import com.github.rpc0.invoke.InvokeSpec;
import io.vertx.core.Future;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;

/**
 * @author fishzhao
 * @since 2021-12-20
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class ServiceLookup {

  private final Map<String, Object> serviceMap;

  @SneakyThrows
  public MethodHandle lookup(@NonNull InvokeSpec invokeSpec) {
    Object service = serviceMap.get(invokeSpec.getCallSiteClassName());
    if (service == null) {
      return null;
    }
    MethodHandle methodHandle = MethodHandles.publicLookup()
            .findVirtual(service.getClass(),
                    invokeSpec.getMethodName(),
                    MethodType.methodType(Future.class, invokeSpec.getMethodType().parameterList()
                    ));
    return methodHandle.bindTo(service);
  }


}
