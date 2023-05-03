package com.github.rpc0.conf;

import java.util.function.Supplier;

/**
 * @author fishzhao
 * @since 2022-01-25
 */
public interface ConstructingProcess<T> extends Supplier<T> {

  T build();

  @Override
  default T get() {
    return build();
  }
}
