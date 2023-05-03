package com.github.rpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.minlog.Log;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import com.github.rpc0.kryo.KryoFactory;
import com.github.rpc0.kryo.KryoRegistry;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class ImmutableMapSerializerTest {


  static {
    Log.TRACE();
  }

  private final Kryo kryo = new KryoFactory(
          getClass().getClassLoader(),
          new KryoRegistry() {
            @Override
            public void registerClasses(Kryo kryo) {
              kryo.register(Foo.class);
            }
          }
  ).get();

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
  public void verifyMap() {
    ImmutableMap<String, String> map1 = ImmutableMap.of();
    assertEquals(map1, roundtrip(map1));
    assertEquals(map1, copy(map1));

    ImmutableMap<String, Integer> map2 = ImmutableMap.of("aaa", 1, "bbb", 2, "ccc", 3);
    assertEquals(map2, roundtrip(map2));
    assertEquals(map2, copy(map2));

    ImmutableMap<String, Object> map3 = ImmutableMap.of(
            "time", LocalDateTime.now(),
            "score", Math.PI,
            "uuid", UUID.randomUUID().toString(),
            "tags", Arrays.asList("aaa", "bbb", "ccc")
    );
    assertEquals(map3, roundtrip(map3));
  }

  @Test
  public void verifyBiMap() {
    ImmutableBiMap<String, String> map1 = ImmutableBiMap.of();
    assertEquals(map1, roundtrip(map1));
    assertEquals(map1, copy(map1));

    ImmutableBiMap<String, Integer> map2 = ImmutableBiMap.of("aaa", 1, "bbb", 2, "ccc", 3);
    assertEquals(map2, roundtrip(map2));
    assertEquals(map2, copy(map2));

    ImmutableMap<String, Set<Integer>> map3 = ImmutableMap.of(
            "time", Set.of(1, 3),
            "score", Set.of(2, 3),
            "uuid", Set.of(1, 3),
            "tags", ImmutableSet.of(4, 5, 6)
    );
    assertEquals(map3, roundtrip(map3));
  }

  @Test
  public void verifySortedMap() {
    ImmutableSortedMap<String, Object> map1 = ImmutableSortedMap.of();
    assertEquals(map1, roundtrip(map1));
    assertEquals(map1, copy(map1));

    ImmutableSortedMap<String, Object> map2 = ImmutableSortedMap.of(
            "time", LocalDateTime.now(),
            "score", Math.PI,
            "uuid", UUID.randomUUID().toString(),
            "tags", Arrays.asList("aaa", "bbb", "ccc")
    );
    assertEquals(map2, roundtrip(map2));

    ImmutableSortedMap<Object, Object> map3 = ImmutableSortedMap
            .orderedBy(Ordering.usingToString())
            .put("1", Set.of("a", "b", "c"))
            .put(2, LocalDateTime.now())
            .put("3", UUID.randomUUID().toString())
            .put(4, "hello world")
            .build();
    assertEquals(map3, roundtrip(map3));
  }

  @Test
  public void verifyObject() {
    Foo foo = new Foo();
    foo.setTime(System.currentTimeMillis());
    foo.setName(UUID.randomUUID().toString());
    foo.setList1(ImmutableList.of("a", 1, "b", 2));
    foo.setList2(ImmutableSortedSet.of(1L, 2L, 3L));
    foo.setMap1(ImmutableMap.of(
            "time", LocalDateTime.now(),
            "uuid", UUID.randomUUID().toString(),
            "score", Math.PI,
            "tags", Arrays.asList("1", "2", "3")
    ));
    foo.setMap2(ImmutableBiMap.of(
            "a", 1,
            "b", 2,
            "c", 3
    ));

    assertEquals(foo, roundtrip(foo));
  }

  @Getter
  @Setter
  public static final class Foo {

    private long time;
    private String name;
    private ImmutableList<Object> list1;
    private ImmutableSortedSet<Long> list2;
    private ImmutableMap<String, Object> map1;
    private ImmutableBiMap<String, Integer> map2;


    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Foo foo = (Foo) o;
      return time == foo.time && Objects.equals(name, foo.name) && Objects.equals(list1, foo.list1) && Objects.equals(list2, foo.list2) && Objects.equals(map1, foo.map1) && Objects.equals(map2, foo.map2);
    }

    @Override
    public int hashCode() {
      return Objects.hash(time, name, list1, list2, map1, map2);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
              .add("time", time)
              .add("name", name)
              .add("list1", list1)
              .add("list2", list2)
              .add("map1", map1)
              .add("map2", map2)
              .toString();
    }
  }
}