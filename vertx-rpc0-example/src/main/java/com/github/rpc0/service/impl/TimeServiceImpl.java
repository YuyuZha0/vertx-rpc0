package com.github.rpc0.service.impl;

import com.github.rpc0.service.TimeService;
import io.vertx.core.Future;
import lombok.NonNull;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * @author fishzhao
 * @since 2022-01-14
 */
public class TimeServiceImpl implements TimeService {

  @Override
  public Future<LocalDateTime> timeAfterNDays(@NonNull LocalDateTime from, int nDays) {
    return Future.succeededFuture(
            from.plusDays(nDays)
    );
  }

  @Override
  public Future<Long> durationMills(@NonNull LocalDateTime from, @NonNull LocalDateTime to) {
    return Future.succeededFuture(Duration.between(from, to).toMillis());
  }
}
