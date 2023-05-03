package com.github.rpc0.service;


import com.github.rpc0.model.User;
import io.vertx.core.Future;

import java.util.List;

/**
 * @author fishzhao
 * @since 2022-01-17
 */
public interface BeanService {

  Future<String> serializeToJson(Object o);

  Future<List<User>> getUserById(List<String> idList);
}
