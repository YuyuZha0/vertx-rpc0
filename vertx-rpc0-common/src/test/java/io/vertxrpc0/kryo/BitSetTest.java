package io.vertxrpc0.kryo;

import io.netty.buffer.ByteBufUtil;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BitSetTest {

  @Test
  public void hexDumpOfBitsOneAndThree() {
    BitSet bitSet = new BitSet(4);
    bitSet.set(1);
    bitSet.set(3);
    assertEquals("0a", ByteBufUtil.hexDump(bitSet.toByteArray()));
  }

  @Test
  public void hexDumpOfEmptyBitSet() {
    assertEquals("", ByteBufUtil.hexDump(new BitSet().toByteArray()));
  }
}
