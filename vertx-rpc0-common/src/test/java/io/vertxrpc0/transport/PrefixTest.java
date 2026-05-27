package io.vertxrpc0.transport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PrefixTest {

  @Test
  public void prefixLenIsSix() {
    assertEquals(6, Prefix.prefixLen());
  }

  @Test
  public void prependToWritesMagicAndLength() {
    byte[] payload = {1, 2, 3, 4};
    ByteBuf body = Unpooled.wrappedBuffer(payload);
    ByteBuf prefixed = Prefix.prependTo(body);
    try {
      assertEquals(Prefix.prefixLen() + payload.length, prefixed.readableBytes());

      byte[] magic = new byte[2];
      prefixed.getBytes(0, magic);
      assertTrue(Prefix.isMagicMatch(magic), "first two bytes must be the protocol magic");

      assertEquals(payload.length, prefixed.getInt(2));

      byte[] tail = new byte[payload.length];
      prefixed.getBytes(6, tail);
      for (int i = 0; i < payload.length; i++) {
        assertEquals(payload[i], tail[i]);
      }
    } finally {
      prefixed.release();
    }
  }

  @Test
  public void prependToEmptyBuffer() {
    ByteBuf empty = Unpooled.buffer(0);
    ByteBuf prefixed = Prefix.prependTo(empty);
    try {
      assertEquals(Prefix.prefixLen(), prefixed.readableBytes());
      assertEquals(0, prefixed.getInt(2));
    } finally {
      prefixed.release();
    }
  }

  @Test
  public void isMagicMatchAcceptsCorrectMagic() {
    ByteBuf prefixed = Prefix.prependTo(Unpooled.buffer(0));
    try {
      byte[] magic = new byte[2];
      prefixed.getBytes(0, magic);
      assertTrue(Prefix.isMagicMatch(magic));
    } finally {
      prefixed.release();
    }
  }

  @Test
  public void isMagicMatchRejectsBadBytes() {
    assertFalse(Prefix.isMagicMatch(new byte[]{0, 0}));
    assertFalse(Prefix.isMagicMatch(new byte[]{(byte) 0xff, (byte) 0xff}));
  }

  @Test
  public void isMagicMatchRejectsWrongLength() {
    assertFalse(Prefix.isMagicMatch(new byte[]{}));
    assertFalse(Prefix.isMagicMatch(new byte[]{0}));
    assertFalse(Prefix.isMagicMatch(new byte[]{0, 0, 0}));
  }
}
