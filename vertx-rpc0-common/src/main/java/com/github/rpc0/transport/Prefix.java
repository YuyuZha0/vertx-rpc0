package com.github.rpc0.transport;

import com.google.common.primitives.Shorts;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;

import java.io.Serializable;

/**
 * @author fishzhao
 * @since 2022-01-24
 */
public final class Prefix implements Serializable {


  private static final byte[] MAGIC = Shorts.toByteArray((short) 1729); //https://en.wikipedia.org/wiki/1729_(number)

  private Prefix() {
    throw new IllegalStateException();
  }

  public static int prefixLen() {
    return 6;
  }


  public static ByteBuf prependTo(ByteBuf byteBuf) {
    int len = byteBuf.readableBytes();
    ByteBuf prefix = Unpooled.buffer(prefixLen());
    prefix.writeBytes(MAGIC);
    prefix.writeInt(len);
    try {
      return Unpooled.wrappedBuffer(prefix, byteBuf);
    } catch (Exception e) {
      ReferenceCountUtil.release(byteBuf);
      throw e;
    }
  }

  public static boolean isMagicMatch(byte[] magic) {
    return magic.length == 2
           && magic[0] == MAGIC[0]
           && magic[1] == MAGIC[1];
  }
}
