package com.github.rpc0.transport;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.FastThreadLocal;
import lombok.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * @author fishzhao
 * @since 2021-12-15
 */
public final class KryoMessageTransport implements MessageTransport {

  private final FastThreadLocal<Kryo> kryoFastThreadLocal;

  public KryoMessageTransport(@NonNull Supplier<? extends Kryo> factory) {
    this.kryoFastThreadLocal = new FastThreadLocal<>() {
      @Override
      protected Kryo initialValue() {
        return Objects.requireNonNull(factory.get(), "null kryo from factory");
      }

      @Override
      protected void onRemoval(Kryo value) {
        value.reset();
      }
    };
  }

  private Kryo getKryo() {
    return kryoFastThreadLocal.get();
  }

  @Override
  public ByteBuf serialize(@NonNull ByteBufAllocator allocator, MessageExchange obj) {
    ByteBuf byteBuf = allocator.buffer();
    try (OutputStream outputStream = new ByteBufOutputStream(byteBuf)) {
      try (Output output = new Output(outputStream)) {
        getKryo().writeClassAndObject(output, obj);
      }
    } catch (Exception e) {
      ReferenceCountUtil.release(byteBuf);
      if (e instanceof KryoException) {
        throw (KryoException) e;
      } else {
        throw new KryoException("Exception while serializing msg:", e);
      }
    }
    return byteBuf;
  }

  @Override
  public MessageExchange deserialize(@NonNull ByteBuf byteBuf) {
    try (InputStream inputStream = new ByteBufInputStream(byteBuf)) {
      try (Input input = new Input(inputStream)) {
        return (MessageExchange) getKryo().readClassAndObject(input);
      }
    } catch (IOException e) {
      throw new KryoException("Exception while deserializing msg:", e);
    } finally {
      ReferenceCountUtil.release(byteBuf);
    }
  }
}
