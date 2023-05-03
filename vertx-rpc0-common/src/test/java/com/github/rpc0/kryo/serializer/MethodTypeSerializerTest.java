package com.github.rpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.github.rpc0.kryo.KryoFactory;
import lombok.SneakyThrows;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class MethodTypeSerializerTest {

  private final Kryo kryo = new KryoFactory().get();


  @SneakyThrows
  private <T> T kryoRoundTrip(T src) {
    byte[] bytes;
    try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
      try (Output output = new Output(byteArrayOutputStream)) {
        kryo.writeObject(output, src);
      }
      bytes = byteArrayOutputStream.toByteArray();
    }
    try (Input input = new Input(bytes)) {
      return (T) kryo.readObject(input, src.getClass());
    }
  }

  @Test
  public void test1() throws Throwable {
    MethodHandle superMethod = MethodHandles.publicLookup()
            .findVirtual(CharSequence.class, "charAt", MethodType.methodType(char.class, int.class));

    MethodHandle m1 = superMethod.bindTo("Hello");
    System.out.println(m1.invoke(2));
    MethodHandle m2 = superMethod.bindTo("World");
    System.out.println(m2.invoke(2));
  }
}