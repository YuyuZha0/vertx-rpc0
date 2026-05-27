package io.vertxrpc0.invoke;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResultCodeTest {

  @Test
  public void test() {
    for (ResultCode resultCode : ResultCode.values()) {
      assertEquals(resultCode, ResultCode.forCode(resultCode.ordinal()));
    }
  }
}