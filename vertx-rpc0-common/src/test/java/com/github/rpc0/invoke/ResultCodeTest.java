package com.github.rpc0.invoke;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ResultCodeTest {

  @Test
  public void test() {
    for (ResultCode resultCode : ResultCode.values()) {
      assertEquals(resultCode, ResultCode.forCode(resultCode.ordinal()));
    }
  }
}