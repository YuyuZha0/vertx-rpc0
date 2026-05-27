package io.vertxrpc0.kryo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBufUtil;
import java.util.BitSet;
import org.junit.jupiter.api.Test;

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
