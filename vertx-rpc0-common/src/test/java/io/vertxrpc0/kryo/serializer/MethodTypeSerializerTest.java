package io.vertxrpc0.kryo.serializer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.vertxrpc0.kryo.KryoFactory;
import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

public class MethodTypeSerializerTest {

  private final Kryo kryo = new KryoFactory().get();

  @SuppressWarnings("unchecked")
  @SneakyThrows
  private <T> T kryoRoundTrip(T src) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (Output output = new Output(out)) {
      kryo.writeObject(output, src);
    }
    byte[] bytes = out.toByteArray();
    try (Input input = new Input(bytes)) {
      return (T) kryo.readObject(input, src.getClass());
    }
  }

  @Test
  public void roundTripVoidNoArg() {
    MethodType type = MethodType.methodType(Void.class);
    assertEquals(type, kryoRoundTrip(type));
  }

  @Test
  public void roundTripReferenceReturnPrimitiveArg() {
    MethodType type = MethodType.methodType(String.class, int.class);
    assertEquals(type, kryoRoundTrip(type));
  }

  @Test
  public void roundTripMixedPrimitiveAndReferenceArgs() {
    MethodType type = MethodType.methodType(String.class, int.class, Object.class, double.class, String.class);
    assertEquals(type, kryoRoundTrip(type));
  }

  @Test
  public void roundTripPreservesUsabilityViaMethodHandles() throws Throwable {
    MethodType type = MethodType.methodType(int.class, String.class);
    MethodType copy = kryoRoundTrip(type);

    MethodHandle handle = MethodHandles.publicLookup()
            .findVirtual(String.class, "indexOf", copy);
    assertEquals(2, (int) handle.invoke("Hello", "ll"));
  }
}
