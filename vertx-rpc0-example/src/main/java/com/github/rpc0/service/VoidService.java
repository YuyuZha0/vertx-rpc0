package com.github.rpc0.service;


import io.vertx.core.Future;

/**
 * @author fishzhao
 * @since 2022-01-14
 */
public interface VoidService {

  Future<Void> run();
}
