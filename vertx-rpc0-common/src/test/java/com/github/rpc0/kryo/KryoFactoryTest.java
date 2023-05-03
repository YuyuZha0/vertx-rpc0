package com.github.rpc0.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.minlog.Log;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.github.rpc0.invoke.InvokeResult;
import com.github.rpc0.invoke.InvokeSpec;
import com.github.rpc0.invoke.ParameterArray;
import com.github.rpc0.invoke.ResultCode;
import io.netty.util.internal.PlatformDependent;
import lombok.SneakyThrows;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.lang.invoke.MethodType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

public class KryoFactoryTest {


  static {
    Log.DEBUG();
  }

  private final Kryo kryo = new KryoFactory().get();

  @SneakyThrows
  @SuppressWarnings("unchecked")
  private <T> T roundtrip(T o) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (Output output = new Output(out)) {
      kryo.writeClassAndObject(output, o);
    }
    try (Input input = new Input(out.toByteArray())) {
      return (T) kryo.readClassAndObject(input);
    }
  }


  @Test
  public void verifyList() {
    List<Integer> origin = ImmutableList.of(1, 2, 3, 4, 5);
    List<Integer> target = roundtrip(origin);

    assertNotSame(origin, target);
    assertEquals(origin, target);

    List<Object> origin1 = Arrays.asList(
            "a", Math.PI, 'c', ImmutableMap.of("key", "value"),
            List.of(0, 4, 5, 7, 8)
    );
    List<Object> target1 = roundtrip(origin1);
    assertNotSame(origin1, target1);
    assertEquals(origin1, target1);
  }

  @Test
  public void verifySet() {
    Set<String> origin = ImmutableSet.of("aaa", "bbb", "ccc");
    Set<String> target = roundtrip(origin);

    assertNotSame(origin, target);
    assertEquals(origin, target);
  }

  @Test
  public void verifySortedSet() {
    SortedSet<Integer> origin = new TreeSet<>((Comparator<? super Integer>) null);
    origin.addAll(Arrays.asList(1, 2, 3, 4, 5));
    SortedSet<Integer> target = roundtrip(origin);
    assertEquals(origin, target);

    origin = ImmutableSortedSet.of(8, 1, 2, 4, 5);
    target = roundtrip(origin);
    assertEquals(origin, target);


    origin = new TreeSet<>(Arrays.asList(8, 1, 2, 4, 5));
    target = roundtrip(origin);
    assertEquals(origin, target);
  }

  @Test
  public void verifyProperties() {
    Properties origin = System.getProperties();
    Properties target = roundtrip(origin);

    assertNotSame(origin, target);
    assertEquals(origin, target);
    target.list(System.out);
  }

  @Test
  public void verifyArray() throws Exception {

    int[] a = ThreadLocalRandom.current().ints(100).toArray();
    int[] a1 = roundtrip(a);
    assertNotSame(a, a1);
    assertArrayEquals(a, a1);

    String[] b = new String[]{
            UUID.randomUUID().toString(),
            null,
            "aaa",
            "bbb",
            "ccc"
    };
    String[] b1 = roundtrip(b);
    assertNotSame(b, b1);
    assertArrayEquals(b, b1);

    Object[] c = new Object[]{
            null,
            1,
            "hello",
            ImmutableSortedSet.of(0, 1, 2, 3, 4),
            LocalDateTime.now(),
            new Date()
    };
    Object[] c1 = roundtrip(c);
    assertNotSame(c, c1);
    assertArrayEquals(c, c1);

    Object[] d = new Serializable[]{
            Integer.MAX_VALUE,
            Long.MAX_VALUE,
            Double.MIN_VALUE,
            UUID.randomUUID().toString(),
            new URL("https://example.com/aaa?foo=bar"),
            OffsetDateTime.MIN
    };
    Object[] d1 = roundtrip(d);
    assertNotSame(d, d1);
    assertArrayEquals(d, d1);
    assertEquals(Object.class, d1.getClass().getComponentType());
  }


  @Test
  public void verifyMap() {
    Map<String, Object> origin = Map.of(
            "key1", Arrays.asList(1, 3, 5, 7, 9),
            "key2", BigDecimal.valueOf(Math.PI),
            "key3", BigInteger.TEN,
            "key4", UUID.randomUUID().toString()
    );
    Map<String, Object> target = roundtrip(origin);
    assertNotSame(origin, target);
    assertEquals(origin, target);
    assertEquals(origin, kryo.copy(target));

    Map<String, Object> origin1 = ImmutableSortedMap.of(
            "key1", Arrays.asList(1, 3, 5, 7, 9),
            "key2", BigDecimal.valueOf(Math.PI),
            "key3", BigInteger.TEN,
            "key4", UUID.randomUUID().toString()
    );
    Map<String, Object> target1 = roundtrip(origin1);
    assertNotSame(origin1, target1);
    assertEquals(origin1, target1);
    assertEquals(origin1, kryo.copy(target1));
  }

  @Test
  public void verifyInvokeSpec() {
    InvokeSpec origin = new InvokeSpec(
            1L,
            System.currentTimeMillis(),
            CharSequence.class.getTypeName(),
            "charAt",
            MethodType.methodType(char.class, int.class),
            ParameterArray.create(new Object[]{13})
    );
    InvokeSpec target = roundtrip(origin);

    assertNotSame(origin, target);
    assertEquals(origin, target);
    assertEquals(origin, kryo.copy(target));
  }

  @Test
  public void verifyInvokeResult() {
    InvokeResult origin = new InvokeResult(1, System.currentTimeMillis(), ResultCode.OK, "", LocalDateTime.now());
    InvokeResult target = roundtrip(origin);

    assertNotSame(origin, target);
    assertEquals(origin, target);
  }

  @Test
  public void verifyQueue() {
    Queue<String> queue = Lists.newLinkedList();
    queue.add("aaaa");
    queue.add("bbbb");
    queue.add("cccc");

    Queue<String> queue1 = roundtrip(queue);
    assertNotSame(queue, queue1);
    assertEquals(queue, queue1);

    queue = PlatformDependent.newMpscQueue(3);
    queue.add("ZZ");
    queue.add("yy");
    queue.add("XXX");

    queue1 = roundtrip(queue);
    assertNotSame(queue, queue1);
    assertArrayEquals(queue.toArray(), queue1.toArray());

    Deque<Integer> deque = new ConcurrentLinkedDeque<>(Arrays.asList(9, 9, 7, 2, 6, 7, 3));
    Deque<Integer> deque1 = roundtrip(deque);
    assertNotSame(deque, deque1);
    assertArrayEquals(deque.toArray(), deque1.toArray());
  }
}