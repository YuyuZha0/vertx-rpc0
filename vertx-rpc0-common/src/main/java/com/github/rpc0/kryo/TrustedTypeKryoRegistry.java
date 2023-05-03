package com.github.rpc0.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.IntMap;
import lombok.NonNull;

/**
 * @author fishzhao
 * @since 2022-01-04
 */
public class TrustedTypeKryoRegistry implements KryoRegistry {

  private final IntMap<Class<?>> customTypeMap;

  public TrustedTypeKryoRegistry(@NonNull IntMap<Class<?>> customTypeMap) {
    this.customTypeMap = new IntMap<>(customTypeMap);
  }

  public TrustedTypeKryoRegistry() {
    this.customTypeMap = new IntMap<>();
  }

  @Override
  public void registerClasses(Kryo kryo) {
    if (customTypeMap.isEmpty()) {
      return;
    }
    final int minId = kryo.getNextRegistrationId() + 1;
    for (IntMap.Entry<Class<?>> entry : customTypeMap.entries()) {
      kryo.register(entry.value, entry.key + minId);
    }

  }
}
