package com.github.rpc0.service;


import io.vertx.core.Future;

import java.util.List;
import java.util.Map;

/**
 * @author fishzhao
 * @since 2021-12-24
 */
public interface StringService {


  Future<List<String>> split(String s, char sep);

  Future<Map<String, String>> getUrlQueryParam(String url);
}
