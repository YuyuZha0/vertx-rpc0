package io.vertxrpc0.conf;

import java.util.function.Supplier;

/**
 * @author fishzhao
 * @since 2022-01-25
 */
public interface ConstructingProcess<T> extends Supplier<T> {

  default T build() {
    return buildSupplier().get();
  }

  @Override
  default T get() {
    return build();
  }

  Supplier<? extends T> buildSupplier();
}
