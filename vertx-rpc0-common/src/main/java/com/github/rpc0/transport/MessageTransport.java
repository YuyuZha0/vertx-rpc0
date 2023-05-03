package com.github.rpc0.transport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

/**
 * @author fishzhao
 * @since 2021-12-15
 */
public interface MessageTransport {

  ByteBuf serialize(ByteBufAllocator allocator, MessageExchange object);

  default ByteBuf serialize(MessageExchange object) {
    return serialize(ByteBufAllocator.DEFAULT, object);
  }

  MessageExchange deserialize(ByteBuf byteBuf);
}
