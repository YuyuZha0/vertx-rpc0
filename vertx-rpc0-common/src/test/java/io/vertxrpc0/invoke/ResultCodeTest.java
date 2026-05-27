package io.vertxrpc0.invoke;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ResultCodeTest {

  @Test
  public void test() {
    for (ResultCode resultCode : ResultCode.values()) {
      assertEquals(resultCode, ResultCode.forCode(resultCode.ordinal()));
    }
  }
}