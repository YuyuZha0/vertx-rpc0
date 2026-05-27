package io.vertxrpc0.service.impl;

import io.vertxrpc0.service.HelloService;
import io.vertx.core.Future;

/**
 * @author fishzhao
 * @since 2022-01-25
 */
public final class HelloServiceImpl implements HelloService {

  @Override
  public Future<String> sayHello(String name) {
    return Future.succeededFuture("Hello, " + name);
  }
}
