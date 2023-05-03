package com.github.rpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Registration;
import lombok.NonNull;

import java.lang.reflect.Constructor;
import java.util.Comparator;
import java.util.function.Function;

/**
 * @author fishzhao
 * @since 2022-01-24
 */
public final class Sorted<T> {

  private final Function<? super Comparator<?>, ? extends T> constructor;

  private Sorted(@NonNull Function<? super Comparator<?>, ? extends T> constructor) {
    this.constructor = constructor;
  }

  public static <E> Sorted<E> of(Function<? super Comparator<?>, ? extends E> constructor) {
    return new Sorted<>(constructor);
  }

  static Class<?> tryResolveType(Kryo kryo, Class<?> type) {
    Registration registration = kryo.getRegistration(type);
    if (registration != null) {
      return registration.getType();
    }
    return type;
  }

  @SuppressWarnings("unchecked")
  public T newInstance(Kryo kryo, Class<? extends T> type, Comparator<?> comparator) {
    if (type.isInterface()) {
      try {
        T sorted = constructor.apply(comparator);
        if (sorted != null) {
          return sorted;
        }
      } catch (Exception ex) {
        throw new KryoException(ex);
      }
    }
    // Use reflection for subclasses.
    try {
      Constructor<?> constructor = type.getConstructor(Comparator.class);
      if (!constructor.canAccess(null)) {
        try {
          constructor.setAccessible(true);
        } catch (SecurityException ignored) {
        }
      }
      return (T) constructor.newInstance(comparator);
    } catch (Exception ex) {
      throw new KryoException(ex);
    }
  }
}
