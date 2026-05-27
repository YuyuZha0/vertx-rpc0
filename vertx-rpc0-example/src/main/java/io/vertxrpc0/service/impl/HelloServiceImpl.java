package io.vertxrpc0.service.impl;

import io.vertx.core.Future;
import io.vertxrpc0.service.HelloService;

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
