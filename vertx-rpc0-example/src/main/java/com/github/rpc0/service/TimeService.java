package com.github.rpc0.service;

import io.vertx.core.Future;

import java.time.LocalDateTime;

/**
 * @author fishzhao
 * @since 2022-01-14
 */
public interface TimeService {

  Future<LocalDateTime> timeAfterNDays(LocalDateTime from, int nDays);

  Future<Long> durationMills(LocalDateTime from, LocalDateTime to);
}
