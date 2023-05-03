package com.github.rpc0.service;

import io.vertx.core.Future;

/**
 * @author fishzhao
 * @since 2021-12-21
 */
public interface DoubleService {

  Future<Double> add(Double a, Double b);

  Future<Double> mul(Double a, Double b);

  Future<Double> add(Object[] doubles);
}
