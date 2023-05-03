package com.github.rpc0.client;

import com.google.common.base.Preconditions;
import io.vertx.core.Closeable;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author fishzhao
 * @since 2021-12-17
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class ServiceFactory implements Closeable {

  private final ConcurrentMap<Class<?>, Object> proxyCache = new ConcurrentHashMap<>();
  private final Set<Class<?>> registry;
  private final Vertx vertx;
  private final ProxyStubSupplier proxyStubSupplier;

  public <T> T create(@NonNull Class<? extends T> type) {
    Preconditions.checkArgument(
            registry.contains(type),
            "`%s` is not registered!",
            type
    );
    Object proxy = Proxy.newProxyInstance(type.getClassLoader(),
            new Class[]{type},
            new ProxyStubInvocationHandler((VertxInternal) vertx, proxyStubSupplier));
    return type.cast(proxy);
  }

  @SuppressWarnings("unchecked")
  public <T> T getOrCreate(@NonNull Class<? extends T> type) {
    return (T) proxyCache.computeIfAbsent(type, key -> create((Class<? extends T>) key));
  }

  @Override
  public void close(Promise<Void> completion) {
    proxyStubSupplier.close(completion);
    proxyCache.clear();
  }
}
