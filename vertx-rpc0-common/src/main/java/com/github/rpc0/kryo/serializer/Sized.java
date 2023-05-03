package com.github.rpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.function.IntFunction;

/**
 * @author fishzhao
 * @since 2022-01-24
 */
public final class Sized<T> {

  private final IntFunction<? extends T> constructor;

  private Sized(@NonNull IntFunction<? extends T> constructor) {
    this.constructor = constructor;
  }

  public static <E> Sized<E> of(IntFunction<? extends E> constructor) {
    return new Sized<>(constructor);
  }

  public T newInstance(Kryo kryo, Class<? extends T> type, int size) {
    if (type.isInterface()) {
      try {
        T instance = constructor.apply(size);
        if (instance != null) {
          return instance;
        }
      } catch (Exception e) {
        throw new KryoException(e);
      }
    }
    T collection = kryo.newInstance(type);
    if (collection instanceof ArrayList) ((ArrayList<?>) collection).ensureCapacity(size);
    return collection;
  }

}
