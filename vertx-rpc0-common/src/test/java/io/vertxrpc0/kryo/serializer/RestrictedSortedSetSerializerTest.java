package io.vertxrpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import io.vertxrpc0.kryo.KryoFactory;
import io.vertxrpc0.testutil.KryoRoundtrip;
import org.junit.jupiter.api.Test;

import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RestrictedSortedSetSerializerTest {

  private final Kryo kryo = new KryoFactory().get();

  @Test
  public void roundTripNull() {
    assertNull(KryoRoundtrip.roundtrip(kryo, (SortedSet<?>) null));
  }

  @Test
  public void roundTripTreeSet() {
    TreeSet<Integer> in = new TreeSet<>();
    in.add(5);
    in.add(1);
    in.add(3);

    SortedSet<?> back = KryoRoundtrip.roundtrip(kryo, in);
    assertEquals(in, back);
    assertEquals(1, back.first());
    assertEquals(5, back.last());
  }

  @Test
  public void roundTripEmptyTreeSet() {
    SortedSet<?> back = KryoRoundtrip.roundtrip(kryo, new TreeSet<>());
    assertEquals(0, back.size());
  }
}
