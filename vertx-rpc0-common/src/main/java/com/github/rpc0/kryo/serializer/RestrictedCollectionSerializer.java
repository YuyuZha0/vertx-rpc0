package com.github.rpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.serializers.CollectionSerializer;
import lombok.NonNull;

import java.util.Collection;

/**
 * @author fishzhao
 * @since 2022-01-21
 */
public final class RestrictedCollectionSerializer<T extends Collection<?>> extends CollectionSerializer<T> {

  private final Sized<T> sized;

  public RestrictedCollectionSerializer(@NonNull Sized<T> sized) {
    this.sized = sized;
  }

  @Override
  protected T create(Kryo kryo, Input input, Class<? extends T> type, int size) {
    return sized.newInstance(kryo, type, size);
  }
}
