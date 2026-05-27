package io.vertxrpc0.transport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;

@SuppressWarnings("deprecation")
public final class BufferUtil {

  private BufferUtil() {
    throw new IllegalStateException();
  }

  public static Buffer fromByteBuf(ByteBuf byteBuf) {
    return Buffer.buffer(byteBuf);
  }

  public static ByteBuf toByteBuf(Buffer buffer) {
    return buffer.getByteBuf();
  }

  public static Buffer fromBytes(byte[] bytes) {
    // Don't use Buffer.buffer(bytes) since it will copy the bytes, and we want to avoid unnecessary
    // copying.
    return Buffer.buffer(Unpooled.wrappedBuffer(bytes));
  }
}
