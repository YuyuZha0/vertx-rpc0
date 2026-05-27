package io.vertxrpc0.kryo.serializer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esotericsoftware.kryo.Kryo;
import io.vertxrpc0.invoke.ParameterArray;
import io.vertxrpc0.kryo.KryoFactory;
import io.vertxrpc0.testutil.KryoRoundtrip;
import org.junit.jupiter.api.Test;

public class ParameterArraySerializerTest {

  private final Kryo kryo = new KryoFactory().get();

  @Test
  public void roundTripEmpty() {
    ParameterArray empty = ParameterArray.create();
    assertEquals(empty, KryoRoundtrip.roundtrip(kryo, empty));
  }

  @Test
  public void roundTripMixedNullAndValues() {
    ParameterArray pa = ParameterArray.create(new Object[] {1, "two", null, 3.14d});
    assertEquals(pa, KryoRoundtrip.roundtrip(kryo, pa));
  }

  @Test
  public void roundTripSingleNullArg() {
    ParameterArray pa = ParameterArray.create(new Object[] {null});
    assertEquals(pa, KryoRoundtrip.roundtrip(kryo, pa));
  }
}
