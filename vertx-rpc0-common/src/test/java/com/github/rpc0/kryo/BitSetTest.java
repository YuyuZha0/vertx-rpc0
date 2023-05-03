package com.github.rpc0.kryo;

import io.netty.buffer.ByteBufUtil;
import org.junit.Test;

import java.util.BitSet;

/**
 * @author fishzhao
 * @since 2022-02-18
 */
public class BitSetTest {

  @Test
  public void test(){
    BitSet bitSet = new BitSet(4);
    bitSet.set(1);
    bitSet.set(3);
    System.out.println(ByteBufUtil.hexDump(bitSet.toByteArray()));
  }
}
