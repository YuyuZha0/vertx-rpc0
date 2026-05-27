package io.vertxrpc0.testutil;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.ByteArrayOutputStream;

public final class KryoRoundtrip {

  private KryoRoundtrip() {}

  @SuppressWarnings("unchecked")
  public static <T> T roundtrip(Kryo kryo, T src) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (Output output = new Output(out)) {
      kryo.writeClassAndObject(output, src);
    }
    try (Input input = new Input(out.toByteArray())) {
      return (T) kryo.readClassAndObject(input);
    }
  }
}
