package com.github.rpc0.reflection;

import com.esotericsoftware.kryo.util.GenericsUtil;
import io.vertx.core.Future;

import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;

/**
 * @author fishzhao
 * @since 2021-12-31
 */
public final class ReflectionUtil {


  private ReflectionUtil() {
    throw new IllegalStateException();
  }

  public static Class<?> getFutureResultType(Type type) {
    return (Class<?>) GenericsUtil.resolveTypeParameters(Future.class, Future.class, type)[0];
  }

  public static Class<?> getCollectionElementType(Type type) {
    return (Class<?>) GenericsUtil.resolveTypeParameters(Collection.class, Collection.class, type)[0];
  }

  public static Map.Entry<Class<?>, Class<?>> getMapKeyValueType(Type type) {
    Type[] parameterTypes = GenericsUtil.resolveTypeParameters(Map.class, Map.class, type);
    return new AbstractMap.SimpleEntry<>(
            (Class<?>) parameterTypes[0],
            (Class<?>) parameterTypes[1]
    );
  }
}
