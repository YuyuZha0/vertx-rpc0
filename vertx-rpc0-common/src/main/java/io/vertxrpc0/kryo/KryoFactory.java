package io.vertxrpc0.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.SerializerFactory;
import com.esotericsoftware.kryo.serializers.DefaultArraySerializers;
import com.esotericsoftware.kryo.serializers.DefaultSerializers;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.netty.util.AsciiString;
import io.vertx.core.buffer.Buffer;
import io.vertxrpc0.invoke.InvokeResult;
import io.vertxrpc0.invoke.InvokeSpec;
import io.vertxrpc0.invoke.ParameterArray;
import io.vertxrpc0.kryo.serializer.AsciiStringSerializer;
import io.vertxrpc0.kryo.serializer.BufferSerializer;
import io.vertxrpc0.kryo.serializer.ComparatorSerializer;
import io.vertxrpc0.kryo.serializer.ImmutableCollectionSerializer;
import io.vertxrpc0.kryo.serializer.ImmutableMapSerializer;
import io.vertxrpc0.kryo.serializer.InvokeResultSerializer;
import io.vertxrpc0.kryo.serializer.InvokeSpecSerializer;
import io.vertxrpc0.kryo.serializer.MethodTypeSerializer;
import io.vertxrpc0.kryo.serializer.ParameterArraySerializer;
import io.vertxrpc0.kryo.serializer.PropertiesSerializer;
import io.vertxrpc0.kryo.serializer.RestrictedCollectionSerializer;
import io.vertxrpc0.kryo.serializer.RestrictedMapSerializer;
import io.vertxrpc0.kryo.serializer.RestrictedSortedMapSerializer;
import io.vertxrpc0.kryo.serializer.RestrictedSortedSetSerializer;
import io.vertxrpc0.kryo.serializer.Sized;
import io.vertxrpc0.kryo.serializer.Sorted;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Supplier;
import lombok.NonNull;

/**
 * @author fishzhao
 * @since 2021-12-15
 */
public final class KryoFactory implements Supplier<Kryo> {

  private static final Set<Class<?>> VALUE_TYPES =
      // Insertion-ordered: registration ids are positional (see get()), so this iteration order is
      // part of the wire contract. A HashSet here would assign unstable ids across JVMs/versions
      // and silently break cross-peer compatibility.
      ImmutableSet.<Class<?>>builder()
          .add(Byte.class)
          .add(Boolean.class)
          .add(Character.class)
          .add(Short.class)
          .add(Integer.class)
          .add(Float.class)
          .add(Double.class)
          .add(String.class)
          .add(BitSet.class)
          .add(URL.class)
          .add(Charset.class)
          .add(Currency.class)
          .add(BigInteger.class)
          .add(BigDecimal.class)
          .add(Date.class)
          .add(Calendar.class)
          .add(TimeZone.class)
          .add(LocalDate.class)
          .add(LocalTime.class)
          .add(LocalDateTime.class)
          .add(OffsetDateTime.class)
          .add(ZonedDateTime.class)
          .add(Duration.class)
          .add(ZoneId.class)
          .add(Instant.class)
          .build();

  private final ClassLoader classLoader;
  private final KryoRegistry registry;

  public KryoFactory(@NonNull ClassLoader classLoader, @NonNull KryoRegistry registry) {
    this.classLoader = classLoader;
    this.registry = registry;
  }

  public KryoFactory() {
    this(Kryo.class.getClassLoader(), new TrustedTypeKryoRegistry());
  }

  public static Set<Class<?>> getValueTypes() {
    return VALUE_TYPES;
  }

  public static boolean isValueOrValueArrayType(Class<?> type) {
    Class<?> componentType;
    return type != null
        && (VALUE_TYPES.contains(type)
            || (type.isArray()
                && ((componentType = type.getComponentType()).isPrimitive()
                    || VALUE_TYPES.contains(componentType))));
  }

  /**
   * Produces a fully-registered {@link Kryo}. Every instance handed out registers the same classes
   * in the same order, and that order is part of the wire contract.
   *
   * <p>Kryo writes a class's <em>registration id</em> (a varint), not its name, onto the wire. A
   * class registered without an explicit id gets the next sequential id, so the id is a pure
   * function of registration order. Two peers interoperate only if they register the same classes
   * in the same sequence: reorder a single {@code register(...)} call and every id after it shifts
   * by one, so the receiver decodes id N as the wrong class — silent data corruption or a
   * ClassCastException, not a clean protocol error. Client and server both build their Kryo through
   * this factory, so the fixed sequence below is what keeps them in agreement.
   *
   * <p>Two consequences this relies on:
   *
   * <ul>
   *   <li>Anything iterated while registering must have a deterministic order — see {@link
   *       #VALUE_TYPES} (an insertion-ordered ImmutableSet, not a HashSet).
   *   <li>User {@code @TrustedType} classes are registered last and with <em>explicit</em> ids
   *       (from {@code typeId}), so they're immune to this ordering and both sides only need to
   *       agree on each id, not on insertion order.
   * </ul>
   */
  @Override
  public Kryo get() {
    Kryo kryo = new SafeKryo();
    kryo.setClassLoader(classLoader);
    kryo.setDefaultSerializer(new SerializerFactory.VersionFieldSerializerFactory());
    kryo.register(Void.class, new DefaultSerializers.VoidSerializer());
    kryo.register(ParameterArray.class, new ParameterArraySerializer());
    kryo.register(MethodType.class, new MethodTypeSerializer());
    kryo.register(InvokeSpec.class, new InvokeSpecSerializer());
    kryo.register(InvokeResult.class, new InvokeResultSerializer());

    registerValueTypes(kryo);
    registerArrayTypes(kryo);
    registerCollectionTypes(kryo);
    registerMapTypes(kryo);

    kryo.register(Comparator.class, new ComparatorSerializer());
    kryo.register(Object.class);

    // User @TrustedType classes go last, registered with their explicit typeIds — see get().
    registry.registerClasses(kryo);

    return kryo;
  }

  private void registerValueTypes(Kryo kryo) {
    for (Class<?> type : getValueTypes()) {
      kryo.register(type);
    }
    kryo.register(AsciiString.class, new AsciiStringSerializer());
    kryo.register(Buffer.class, new BufferSerializer());
  }

  private void registerArrayTypes(Kryo kryo) {
    kryo.register(byte[].class);
    kryo.register(boolean[].class);
    kryo.register(short[].class);
    kryo.register(int[].class);
    kryo.register(long[].class);
    kryo.register(float[].class);
    kryo.register(double[].class);
    kryo.register(char[].class);
    kryo.register(String[].class);
    kryo.register(Object[].class);
    for (Class<?> type : getValueTypes()) {
      if (type != String.class) {
        Class<?> arrayType = Array.newInstance(type, 0).getClass();
        kryo.register(
            arrayType, new DefaultArraySerializers.ObjectArraySerializer(kryo, arrayType));
      }
    }
  }

  private void registerCollectionTypes(Kryo kryo) {
    kryo.register(ArrayList.class);
    kryo.register(LinkedList.class);
    kryo.register(LinkedHashSet.class);
    kryo.register(HashSet.class);
    kryo.register(TreeSet.class);
    kryo.register(ArrayDeque.class);
    kryo.register(PriorityQueue.class);

    kryo.register(List.class, new RestrictedCollectionSerializer<>(Sized.of(ArrayList::new)));
    kryo.register(
        Set.class,
        new RestrictedCollectionSerializer<>(Sized.of(Sets::newHashSetWithExpectedSize)));
    kryo.register(Queue.class, new RestrictedCollectionSerializer<>(Sized.of(ArrayDeque::new)));
    kryo.register(Deque.class, new RestrictedCollectionSerializer<>(Sized.of(ArrayDeque::new)));
    kryo.register(SortedSet.class, new RestrictedSortedSetSerializer<>(Sorted.of(TreeSet::new)));
    kryo.register(Collection.class, new RestrictedCollectionSerializer<>(Sized.of(ArrayList::new)));
    kryo.register(ImmutableCollection.class, new ImmutableCollectionSerializer<>());
  }

  private void registerMapTypes(Kryo kryo) {
    kryo.register(HashMap.class);
    kryo.register(LinkedHashMap.class);
    kryo.register(TreeMap.class);
    kryo.register(Properties.class, new PropertiesSerializer());

    kryo.register(
        Map.class, new RestrictedMapSerializer<>(Sized.of(Maps::newHashMapWithExpectedSize)));
    kryo.register(SortedMap.class, new RestrictedSortedMapSerializer<>(Sorted.of(TreeMap::new)));
    kryo.register(ImmutableMap.class, new ImmutableMapSerializer<>());
  }
}
