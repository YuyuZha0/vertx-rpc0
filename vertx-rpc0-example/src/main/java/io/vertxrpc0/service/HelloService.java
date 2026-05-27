package io.vertxrpc0.service;

import io.vertx.core.Future;

/**
 * @author fishzhao
 * @since 2022-01-25
 */
public interface HelloService {

  Future<String> sayHello(String name);
}
