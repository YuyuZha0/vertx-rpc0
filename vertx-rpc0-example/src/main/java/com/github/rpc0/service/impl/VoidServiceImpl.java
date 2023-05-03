package com.github.rpc0.service.impl;

import com.github.rpc0.service.VoidService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;

/**
 * @author fishzhao
 * @since 2022-01-14
 */
@RequiredArgsConstructor
public class VoidServiceImpl implements VoidService {

  private final Vertx vertx;

  @Override
  public Future<Void> run() {
    Promise<Void> promise = Promise.promise();
    vertx.setTimer(200, timerId -> promise.complete());
    return promise.future();
  }
}
