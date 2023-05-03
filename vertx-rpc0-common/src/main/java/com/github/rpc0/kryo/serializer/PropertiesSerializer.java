package com.github.rpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.io.ByteStreams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * @author fishzhao
 * @since 2022-01-23
 */
public final class PropertiesSerializer extends Serializer<Properties> {

  {
    setAcceptsNull(true);
  }

  @Override
  public void write(Kryo kryo, Output output, Properties properties) {
    if (properties == null) {
      output.writeByte(0);
      return;
    }
    if (properties.isEmpty()) {
      output.writeByte(1);
      return;
    }
    ByteBuf buf = Unpooled.buffer();
    try (OutputStream outputStream = new ByteBufOutputStream(buf)) {
      try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
        try (BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter)) {
          properties.store(bufferedWriter, null);
        }
      }
    } catch (IOException e) {
      throw new KryoException("Exception while writing properties", e);
    }
    output.writeInt(buf.readableBytes() + 1, true);
    try (InputStream in = new ByteBufInputStream(buf)) {
      ByteStreams.copy(in, output);
    } catch (IOException e) {
      throw new KryoException("Exception while writing properties", e);
    }
  }

  @Override
  public Properties read(Kryo kryo, Input input, Class<? extends Properties> type) {
    int length = input.readInt(true) - 1;
    if (length == -1) {
      return null;
    }
    Properties properties = new Properties();
    if (length == 0) {
      return properties;
    }
    byte[] bytes = input.readBytes(length);
    try (InputStream in = new ByteBufInputStream(Unpooled.wrappedBuffer(bytes))) {
      try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
        properties.load(reader);
      }
    } catch (IOException e) {
      throw new KryoException("Exception while reading properties", e);
    }
    return properties;
  }

  @Override
  public Properties copy(Kryo kryo, Properties original) {
    if (original == null) {
      return null;
    }
    return (Properties) original.clone();
  }
}
