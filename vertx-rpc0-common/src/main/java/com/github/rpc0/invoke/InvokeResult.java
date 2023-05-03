package com.github.rpc0.invoke;

import com.github.rpc0.transport.MessageExchange;
import com.google.common.base.MoreObjects;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

/**
 * @author fishzhao
 * @since 2021-12-16
 */
@RequiredArgsConstructor
@Getter
public final class InvokeResult implements MessageExchange, Serializable {

  private static final long serialVersionUID = 780334435307337156L;
  private final long requestId;
  private final long timestamp;
  private final ResultCode code;
  private final String errorMessage;
  private final Object result;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    InvokeResult that = (InvokeResult) o;
    return requestId == that.requestId
           && timestamp == that.timestamp
           && code == that.code
           && Objects.equals(errorMessage, that.errorMessage)
           && Objects.equals(result, that.result);
  }

  @Override
  public int hashCode() {
    return Objects.hash(requestId, timestamp, code, errorMessage, result);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
            .omitNullValues()
            .add("requestId", requestId)
            .add("timestamp", timestamp)
            .add("code", code)
            .add("errorMessage", errorMessage)
            .add("result", result)
            .toString();
  }
}
