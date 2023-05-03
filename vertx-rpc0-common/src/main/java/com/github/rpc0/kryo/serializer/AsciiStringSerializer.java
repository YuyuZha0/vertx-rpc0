package com.github.rpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.netty.util.AsciiString;

import static com.esotericsoftware.kryo.Kryo.NULL;

/**
 * @author fishzhao
 * @since 2022-02-24
 */
public final class AsciiStringSerializer extends Serializer<AsciiString> {

  @Override
  public void write(Kryo kryo, Output output, AsciiString object) {
    if (object == null) {
      output.writeByte(NULL);
      return;
    }
    int length = object.length();
    output.writeVarInt(length + 1, true);
    if (length > 0) {
      output.writeBytes(object.array());
    }
  }

  @Override
  public AsciiString read(Kryo kryo, Input input, Class<? extends AsciiString> type) {
    int length = input.readByte();
    if (length == NULL) {
      return null;
    }
    return new AsciiString(input.readBytes(length - 1), false);
  }
}
