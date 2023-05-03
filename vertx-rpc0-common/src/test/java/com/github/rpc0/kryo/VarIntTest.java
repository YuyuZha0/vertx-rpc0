package com.github.rpc0.kryo;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.netty.buffer.ByteBufUtil;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

/**
 * @author fishzhao
 * @since 2022-02-17
 */
public class VarIntTest {

  @Test
  public void test() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(64);
    try (Output output = new Output(outputStream)) {
      //output.writeVarIntFlag(true, 0xffff, true);
      output.writeVarIntFlag(false, 0xffff, true);
    }
    byte[] bytes = outputStream.toByteArray();
    System.out.println(ByteBufUtil.hexDump(bytes));
    try (Input input = new Input(bytes)) {
      System.out.println(input.readVarIntFlag());
      int varIntFlag = input.readVarIntFlag(true);
      System.out.println(Integer.toHexString(varIntFlag));
    }
  }
}
