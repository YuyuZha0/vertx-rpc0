package com.github.rpc0.invoke;

public enum ResultCode {
  OK,
  PROTOCOL_ERROR,
  PARAMETER_ERROR,
  LOOKUP_ERROR,
  INVOCATION_ERROR,
  UNKNOWN_ERROR;

  private static final ResultCode[] LOOKUP = ResultCode.values();


  public static ResultCode forCode(int code) {
    if (code < 0 || code >= LOOKUP.length) {
      return null;
    }
    return LOOKUP[code];
  }
}
