package com.github.rpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.minlog.Log;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import com.github.rpc0.kryo.KryoFactory;
import lombok.SneakyThrows;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class ImmutableCollectionSerializerTest {

  static {
    Log.TRACE();
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

  private <T> T copy(T o) {
    return kryo.copy(o);
  }

  @Test
  public void verifyList() {

    ImmutableList<String> list1 = ImmutableList.of();
    assertEquals(list1, roundtrip(list1));
    assertEquals(list1, copy(list1));

    ImmutableList<String> list2 = ImmutableList.of("aaa", "bbb", "ccc");
    assertEquals(list2, roundtrip(list2));
    assertEquals(list2, copy(list2));

    ImmutableList<Object> list3 = ImmutableList.of(
            Math.PI, "hello", Set.of(1, 2, 3)
    );
    assertEquals(list3, roundtrip(list3));
  }

  @Test
  public void verifySet() {

    ImmutableSet<String> set1 = ImmutableSet.of();
    assertEquals(set1, roundtrip(set1));
    assertEquals(set1, copy(set1));

    ImmutableSet<String> set2 = ImmutableSet.of("aaa", "bbb", "ccc");
    assertEquals(set2, roundtrip(set2));
    assertEquals(set2, copy(set2));

    ImmutableSet<Object> set3 = ImmutableSet.of(1, 3, 6, 'a', 'b', ImmutableSet.of("a", "b", "c"));
    assertEquals(set3, roundtrip(set3));
    assertEquals(set3, copy(set3));
  }

  @Test
  public void verifySortedSet() {
    ImmutableSortedSet<Integer> sortedSet1 = ImmutableSortedSet.of();
    assertEquals(sortedSet1, roundtrip(sortedSet1));
    assertEquals(sortedSet1, copy(sortedSet1));

    ImmutableSortedSet<Double> sortedSet2 = ImmutableSortedSet.of(1D, 3D, 2D, 8D);
    assertEquals(sortedSet2, roundtrip(sortedSet2));
    assertEquals(sortedSet2, copy(sortedSet2));

    ImmutableSortedSet<Object> sortedSet3 = ImmutableSortedSet
            .orderedBy(Ordering.usingToString())
            .add("a", 2, "c", 4L, "e").build();
    assertEquals(sortedSet3, roundtrip(sortedSet3));
    assertEquals(sortedSet3, copy(sortedSet3));
  }

}