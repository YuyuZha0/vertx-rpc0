package com.github.rpc0.invoke;

import com.github.rpc0.transport.MessageExchange;
import com.google.common.base.MoreObjects;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.lang.invoke.MethodType;
import java.util.Objects;

/**
 * @author fishzhao
 * @since 2021-12-13
 */
@Getter
@RequiredArgsConstructor
public final class InvokeSpec implements MessageExchange, Serializable {

  private static final long serialVersionUID = -1103942057244279626L;

  private final long requestId;
  private final long timestamp;
  private final String callSiteClassName;
  private final String methodName;
  private final MethodType methodType;
  private final ParameterArray parameters;

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
            .add("requestId", requestId)
            .add("timestamp", timestamp)
            .add("callSiteClassName", callSiteClassName)
            .add("methodName", methodName)
            .add("methodType", methodType)
            .add("parameters", parameters)
            .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    InvokeSpec that = (InvokeSpec) o;
    return requestId == that.requestId
           && timestamp == that.timestamp
           && callSiteClassName.equals(that.callSiteClassName)
           && methodName.equals(that.methodName)
           && methodType.equals(that.methodType)
           && parameters.equals(that.parameters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(requestId, timestamp, callSiteClassName, methodName, methodType, parameters);
  }
}
