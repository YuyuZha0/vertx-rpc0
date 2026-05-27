package io.vertxrpc0.transport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;

public class BufferUtilTest {

  @Test
  public void fromByteBufExposesByteBufContent() {
    byte[] payload = {10, 20, 30, 40};
    ByteBuf byteBuf = Unpooled.wrappedBuffer(payload);

    Buffer buffer = BufferUtil.fromByteBuf(byteBuf);

    assertEquals(payload.length, buffer.length());
    assertArrayEquals(payload, buffer.getBytes());
  }

  @Test
  public void fromByteBufSharesBackingMemory() {
    ByteBuf byteBuf = Unpooled.buffer(4).writeByte(1).writeByte(2).writeByte(3).writeByte(4);

    Buffer buffer = BufferUtil.fromByteBuf(byteBuf);
    byteBuf.setByte(0, 99);

    assertEquals((byte) 99, buffer.getByte(0));
  }

  @Test
  public void toByteBufExposesBufferContent() {
    byte[] payload = {5, 6, 7, 8, 9};
    Buffer buffer = Buffer.buffer(payload);

    ByteBuf byteBuf = BufferUtil.toByteBuf(buffer);

    assertEquals(payload.length, byteBuf.readableBytes());
    byte[] out = new byte[payload.length];
    byteBuf.getBytes(byteBuf.readerIndex(), out);
    assertArrayEquals(payload, out);
  }

  @Test
  public void toByteBufReturnsIndependentIndices() {
    // BufferImpl#getByteBuf returns a slice with its own reader/writer indices.
    // Draining the returned ByteBuf must not affect subsequent reads from the source Buffer.
    Buffer buffer = Buffer.buffer(new byte[] {1, 2, 3, 4});

    ByteBuf byteBuf = BufferUtil.toByteBuf(buffer);
    byteBuf.readBytes(new byte[byteBuf.readableBytes()]);

    assertEquals(4, buffer.length());
    assertEquals((byte) 1, buffer.getByte(0));
  }

  @Test
  public void fromBytesExposesContentWithoutCopy() {
    byte[] payload = {100, 101, 102, 103};

    Buffer buffer = BufferUtil.fromBytes(payload);

    assertEquals(payload.length, buffer.length());
    assertArrayEquals(payload, buffer.getBytes());

    // fromBytes wraps the array (Unpooled.wrappedBuffer) instead of copying.
    payload[0] = 0;
    assertEquals((byte) 0, buffer.getByte(0));
  }

  @Test
  public void fromBytesEmpty() {
    Buffer buffer = BufferUtil.fromBytes(new byte[0]);
    assertEquals(0, buffer.length());
  }

  @Test
  public void fromByteBufThenToByteBufRoundTrip() {
    byte[] payload = {-1, 0, 1, 2, 3};
    ByteBuf original = Unpooled.wrappedBuffer(payload);

    Buffer buffer = BufferUtil.fromByteBuf(original);
    ByteBuf roundTripped = BufferUtil.toByteBuf(buffer);

    byte[] out = new byte[payload.length];
    roundTripped.getBytes(roundTripped.readerIndex(), out);
    assertArrayEquals(payload, out);
  }
}
