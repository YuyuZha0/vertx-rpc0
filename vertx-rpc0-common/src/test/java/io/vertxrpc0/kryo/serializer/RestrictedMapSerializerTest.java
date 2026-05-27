package io.vertxrpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import io.vertxrpc0.kryo.KryoFactory;
import io.vertxrpc0.testutil.KryoRoundtrip;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RestrictedMapSerializerTest {

  private final Kryo kryo = new KryoFactory().get();

  @Test
  public void roundTripNull() {
    assertNull(KryoRoundtrip.roundtrip(kryo, (Map<?, ?>) null));
  }

  @Test
  public void roundTripHashMap() {
    Map<String, Integer> in = new HashMap<>();
    in.put("a", 1);
    in.put("b", 2);
    in.put("c", 3);
    Map<?, ?> back = KryoRoundtrip.roundtrip(kryo, in);
    assertEquals(in, back);
  }

  @Test
  public void roundTripLinkedHashMap() {
    Map<String, Integer> in = new LinkedHashMap<>();
    in.put("a", 1);
    in.put("b", 2);
    Map<?, ?> back = KryoRoundtrip.roundtrip(kryo, in);
    assertEquals(in, back);
  }

  @Test
  public void roundTripEmptyMap() {
    Map<?, ?> back = KryoRoundtrip.roundtrip(kryo, new HashMap<>());
    assertEquals(0, back.size());
  }
}
