package com.github.rpc0.service.impl;

import com.github.rpc0.service.DoubleService;
import io.vertx.core.Future;
import lombok.NonNull;

import java.util.Arrays;

/**
 * @author fishzhao
 * @since 2021-12-21
 */
public class DoubleServiceImpl implements DoubleService {

  @Override
  public Future<Double> add(@NonNull Double a, @NonNull Double b) {
    return Future.succeededFuture(a + b);
  }

  @Override
  public Future<Double> mul(Double a, Double b) {
    return Future.succeededFuture(a * b);
  }

  @Override
  public Future<Double> add(Object[] doubles) {
    return Future.succeededFuture(
            Arrays.stream(doubles).map(o -> (Double) o).mapToDouble(Double::doubleValue).sum()
    );
  }
}
