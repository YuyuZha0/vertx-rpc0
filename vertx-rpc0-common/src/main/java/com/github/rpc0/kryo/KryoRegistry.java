package com.github.rpc0.kryo;

import com.esotericsoftware.kryo.Kryo;

/**
 * @author fishzhao
 * @since 2022-01-04
 */
@FunctionalInterface
public interface KryoRegistry {

  void registerClasses(Kryo kryo);
}
