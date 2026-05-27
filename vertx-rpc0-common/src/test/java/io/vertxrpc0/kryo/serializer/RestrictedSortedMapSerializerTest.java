package io.vertxrpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import io.vertxrpc0.kryo.KryoFactory;
import io.vertxrpc0.testutil.KryoRoundtrip;
import org.junit.jupiter.api.Test;

import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RestrictedSortedMapSerializerTest {

  private final Kryo kryo = new KryoFactory().get();

  @Test
  public void roundTripNull() {
    assertNull(KryoRoundtrip.roundtrip(kryo, (SortedMap<?, ?>) null));
  }

  @Test
  public void roundTripTreeMap() {
    TreeMap<String, Integer> in = new TreeMap<>();
    in.put("c", 3);
    in.put("a", 1);
    in.put("b", 2);

    SortedMap<?, ?> back = KryoRoundtrip.roundtrip(kryo, in);
    assertEquals(in, back);
    assertEquals("a", back.firstKey());
    assertEquals("c", back.lastKey());
  }

  @Test
  public void roundTripEmptyTreeMap() {
    SortedMap<?, ?> back = KryoRoundtrip.roundtrip(kryo, new TreeMap<>());
    assertEquals(0, back.size());
  }
}
