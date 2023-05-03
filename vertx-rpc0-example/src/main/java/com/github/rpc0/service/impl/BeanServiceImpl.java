package com.github.rpc0.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rpc0.model.User;
import com.github.rpc0.service.BeanService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


/**
 * @author fishzhao
 * @since 2022-01-17
 */
@RequiredArgsConstructor
public class BeanServiceImpl implements BeanService {

  private final ObjectMapper objectMapper;

  @Override
  public Future<String> serializeToJson(Object o) {
    Promise<String> promise = Promise.promise();
    try {
      promise.complete(objectMapper.writeValueAsString(o));
    } catch (IOException e) {
      promise.tryFail(e);
    }
    return promise.future();
  }

  @Override
  public Future<List<User>> getUserById(List<String> idList) {
    if (idList == null || idList.isEmpty()) {
      return Future.succeededFuture(Collections.emptyList());
    }
    return Future.succeededFuture(
            idList.stream().map(s -> {
              User user = User.generate();
              user.setName(s);
              return user;
            }).collect(Collectors.toList())
    );
  }
}
