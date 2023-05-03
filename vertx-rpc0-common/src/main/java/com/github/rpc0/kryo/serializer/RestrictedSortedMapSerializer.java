package com.github.rpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.MapSerializer;
import lombok.NonNull;

import java.util.Comparator;
import java.util.SortedMap;

/**
 * @author fishzhao
 * @since 2022-01-21
 */
public final class RestrictedSortedMapSerializer<T extends SortedMap<?, ?>> extends MapSerializer<T> {

  private final Sorted<T> sorted;

  public RestrictedSortedMapSerializer(@NonNull Sorted<T> sorted) {
    this.sorted = sorted;
  }

  protected void writeHeader(Kryo kryo, Output output, T map) {
    kryo.writeClassAndObject(output, map.comparator());
  }

  protected T create(Kryo kryo, Input input, Class<? extends T> type, int size) {
    return sorted.newInstance(kryo, type, (Comparator<?>) kryo.readClassAndObject(input));
  }

  @SuppressWarnings("unchecked")
  protected T createCopy(Kryo kryo, T original) {
    return sorted.newInstance(kryo, (Class<? extends T>) original.getClass(), original.comparator());
  }


}
