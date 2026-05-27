package io.vertxrpc0.kryo.serializer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esotericsoftware.kryo.Kryo;
import io.vertxrpc0.invoke.InvokeSpec;
import io.vertxrpc0.invoke.ParameterArray;
import io.vertxrpc0.kryo.KryoFactory;
import io.vertxrpc0.testutil.KryoRoundtrip;
import java.lang.invoke.MethodType;
import org.junit.jupiter.api.Test;

public class InvokeSpecSerializerTest {

  private final Kryo kryo = new KryoFactory().get();

  @Test
  public void roundTripNoArgs() {
    InvokeSpec spec =
        new InvokeSpec(
            1,
            100L,
            "io.example.Svc",
            "ping",
            MethodType.methodType(String.class),
            ParameterArray.create());
    assertEquals(spec, KryoRoundtrip.roundtrip(kryo, spec));
  }

  @Test
  public void roundTripMixedArgs() {
    InvokeSpec spec =
        new InvokeSpec(
            42,
            12345L,
            "io.example.Svc",
            "doStuff",
            MethodType.methodType(String.class, int.class, String.class),
            ParameterArray.create(new Object[] {7, "answer"}));
    assertEquals(spec, KryoRoundtrip.roundtrip(kryo, spec));
  }
}
