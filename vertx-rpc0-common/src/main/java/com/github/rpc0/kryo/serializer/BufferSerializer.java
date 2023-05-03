package com.github.rpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.vertx.core.buffer.Buffer;

import java.io.IOException;

import static com.esotericsoftware.kryo.Kryo.NULL;

/**
 * @author fishzhao
 * @since 2022-02-24
 */
public final class BufferSerializer extends Serializer<Buffer> {

  {
    setAcceptsNull(true);
  }

  @Override
  public void write(Kryo kryo, Output output, Buffer object) {
    if (object == null) {
      output.writeByte(NULL);
      return;
    }
    int length = object.length();
    output.writeVarInt(length + 1, true);
    if (length > 0) {
      try {
        object.getByteBuf().readBytes(output, length);
      } catch (IOException e) {
        throw new KryoException(e);
      }
    }
  }

  @Override
  public Buffer read(Kryo kryo, Input input, Class<? extends Buffer> type) {
    int length = input.readByte();
    if (length == NULL) {
      return null;
    }
    return Buffer.buffer(input.readBytes(length - 1));
  }
}
