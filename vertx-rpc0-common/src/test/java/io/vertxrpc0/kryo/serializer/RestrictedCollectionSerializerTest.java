package io.vertxrpc0.kryo.serializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esotericsoftware.kryo.Kryo;
import io.vertxrpc0.kryo.KryoFactory;
import io.vertxrpc0.testutil.KryoRoundtrip;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class RestrictedCollectionSerializerTest {

  private final Kryo kryo = new KryoFactory().get();

  @Test
  public void roundTripNullList() {
    assertNull(KryoRoundtrip.roundtrip(kryo, (List<?>) null));
  }

  @Test
  public void roundTripArrayList() {
    List<String> in = new ArrayList<>(List.of("a", "b", "c"));
    List<?> back = KryoRoundtrip.roundtrip(kryo, in);
    assertEquals(in, back);
    assertTrue(back instanceof ArrayList);
  }

  @Test
  public void roundTripLinkedList() {
    List<Integer> in = new LinkedList<>(List.of(1, 2, 3));
    List<?> back = KryoRoundtrip.roundtrip(kryo, in);
    assertEquals(in, back);
    assertTrue(back instanceof LinkedList);
  }

  @Test
  public void roundTripHashSet() {
    Set<String> in = new HashSet<>(Set.of("a", "b", "c"));
    Set<?> back = KryoRoundtrip.roundtrip(kryo, in);
    assertEquals(in, back);
  }

  @Test
  public void roundTripQueue() {
    Queue<Integer> in = new ArrayDeque<>(List.of(1, 2, 3));
    Queue<?> back = KryoRoundtrip.roundtrip(kryo, in);
    assertEquals(List.copyOf(in), List.copyOf(back));
  }

  @Test
  public void roundTripDeque() {
    Deque<Integer> in = new ArrayDeque<>(List.of(1, 2, 3));
    Deque<?> back = KryoRoundtrip.roundtrip(kryo, in);
    assertEquals(List.copyOf(in), List.copyOf(back));
  }

  @Test
  public void emptyListSerializesAndReadsBack() {
    Collection<?> back = KryoRoundtrip.roundtrip(kryo, new ArrayList<>());
    assertEquals(0, back.size());
  }
}
