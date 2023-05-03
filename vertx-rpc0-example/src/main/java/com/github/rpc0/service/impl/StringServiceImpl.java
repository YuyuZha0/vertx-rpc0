package com.github.rpc0.service.impl;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.github.rpc0.service.StringService;
import io.vertx.core.Future;

import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * @author fishzhao
 * @since 2021-12-24
 */
public class StringServiceImpl implements StringService {

  @Override
  public Future<List<String>> split(String s, char sep) {
    return Future.succeededFuture(Splitter.on(sep).splitToList(s));
  }

  @SuppressWarnings("UnstableApiUsage")
  @Override
  public Future<Map<String, String>> getUrlQueryParam(String url) {
    if (url == null || url.isEmpty()) {
      return Future.succeededFuture(Collections.emptyMap());
    }
    int split = url.indexOf('?');
    if (split < 0) {
      return Future.succeededFuture(ImmutableMap.of());
    }
    return Future.succeededFuture(
            Splitter.on("&").withKeyValueSeparator("=")
                    .split(url.substring(split + 1))
    );
  }
}
