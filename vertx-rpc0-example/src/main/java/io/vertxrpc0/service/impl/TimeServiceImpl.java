package io.vertxrpc0.service.impl;

import io.vertx.core.Future;
import io.vertxrpc0.service.TimeService;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.NonNull;

/**
 * @author fishzhao
 * @since 2022-01-14
 */
public class TimeServiceImpl implements TimeService {

  @Override
  public Future<LocalDateTime> timeAfterNDays(@NonNull LocalDateTime from, int nDays) {
    return Future.succeededFuture(from.plusDays(nDays));
  }

  @Override
  public Future<Long> durationMills(@NonNull LocalDateTime from, @NonNull LocalDateTime to) {
    return Future.succeededFuture(Duration.between(from, to).toMillis());
  }
}
