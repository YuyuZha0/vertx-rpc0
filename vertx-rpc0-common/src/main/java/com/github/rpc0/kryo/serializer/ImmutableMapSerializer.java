package com.github.rpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ImmutableSerializer;
import com.esotericsoftware.kryo.util.Generics;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;

import java.util.Comparator;
import java.util.Map;
import java.util.SortedMap;

import static com.esotericsoftware.kryo.Kryo.NULL;

/**
 * @author fishzhao
 * @since 2022-02-17
 */
public final class ImmutableMapSerializer<T extends ImmutableMap<?, ?>> extends ImmutableSerializer<T> {


  private static final byte IMMUTABLE = 1;
  private static final byte BI = 2;
  private static final byte SORTED = 3;


  {
    setAcceptsNull(true);
  }


  private void writeComparator(Kryo kryo, Output output, T map) {
    kryo.writeClassAndObject(output, ((SortedMap<?, ?>) map).comparator());
  }


  @Override
  public void write(Kryo kryo, Output output, T map) {
    if (map == null) {
      output.writeByte(NULL);
      return;
    }
    if (map instanceof ImmutableSortedMap) {
      output.writeByte(SORTED);
      writeComparator(kryo, output, map);
    } else if (map instanceof ImmutableBiMap) {
      output.writeByte(BI);
    } else {
      output.writeByte(IMMUTABLE);
    }
    int size = map.size();
    output.writeVarInt(size + 1, true);
    if (size == 0) {
      return;
    }

    Class<?> keyType = null, valueType = null;
    Generics.GenericType[] genericTypes = kryo.getGenerics().nextGenericTypes();
    if (genericTypes != null) {
      Class<?> genericKey = genericTypes[0].resolve(kryo.getGenerics());
      if (genericKey != null && kryo.isFinal(genericKey)) {
        keyType = genericKey;
      }
      Class<?> genericValue = genericTypes[1].resolve(kryo.getGenerics());
      if (genericValue != null && kryo.isFinal(genericValue)) {
        valueType = genericValue;
      }
    }
    if (keyType == null) {
      keyType = RuntimeTypeTrait.elementType(map.keySet());
    }
    if (valueType == null) {
      valueType = RuntimeTypeTrait.elementType(map.values());
    }

    Serializer<?> keySerializer = null, valueSerializer = null;
    if (keyType != null) {
      keySerializer = kryo.getSerializer(keyType);
      if (keySerializer == null) {
        keyType = null;
      }
    }
    if (valueType != null) {
      valueSerializer = kryo.getSerializer(valueType);
      if (valueSerializer == null) {
        valueType = null;
      }
    }
    kryo.writeClass(output, keyType);
    kryo.writeClass(output, valueType);
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (genericTypes != null) kryo.getGenerics().pushGenericType(genericTypes[0]);
      if (keySerializer != null) {
        kryo.writeObject(output, entry.getKey(), keySerializer);
      } else {
        kryo.writeClassAndObject(output, entry.getKey());
      }
      if (genericTypes != null) kryo.getGenerics().popGenericType();
      if (valueSerializer != null) {
        kryo.writeObject(output, entry.getValue(), valueSerializer);
      } else {
        kryo.writeClassAndObject(output, entry.getValue());
      }
    }
  }


  @Override
  @SuppressWarnings({"unchecked", "UnstableApiUsage"})
  public T read(Kryo kryo, Input input, Class<? extends T> type) {
    byte hint = input.readByte();
    if (hint == NULL) {
      return null;
    }
    Comparator<?> comparator = null;
    if (hint == SORTED) {
      comparator = (Comparator<?>) kryo.readClassAndObject(input);
      if (comparator == null) {
        throw new KryoException("Null comparator!");
      }
    }
    int size = input.readVarInt(true) - 1;
    if (size == 0) {
      switch (hint) {
        case IMMUTABLE:
          return (T) ImmutableMap.of();
        case BI:
          return (T) ImmutableBiMap.of();
        case SORTED:
          return (T) (ImmutableSortedMap.orderedBy(comparator).build());
        default:
          throw new KryoException("Unable to deserialize class: " + type);
      }
    }
    ImmutableMap.Builder<Object, Object> builder;
    switch (hint) {
      case IMMUTABLE:
        builder = ImmutableMap.builderWithExpectedSize(size);
        break;
      case BI:
        builder = ImmutableBiMap.builderWithExpectedSize(size);
        break;
      case SORTED:
        builder = ImmutableSortedMap.orderedBy((Comparator<Object>) comparator);
        break;
      default:
        throw new KryoException("Unable to deserialize class: " + type);
    }
    Registration keyRegistration = kryo.readClass(input);
    Registration valueRegistration = kryo.readClass(input);
    Serializer<?> keySerializer = null, valueSerializer = null;
    Class<?> keyType = null, valueType = null;
    if (keyRegistration != null) {
      keyType = keyRegistration.getType();
      keySerializer = kryo.getSerializer(keyType);
    }
    if (valueRegistration != null) {
      valueType = valueRegistration.getType();
      valueSerializer = kryo.getSerializer(valueType);
    }

    Generics.GenericType[] genericTypes = kryo.getGenerics().nextGenericTypes();
    for (int i = 0; i < size; i++) {
      Object key;
      if (genericTypes != null) kryo.getGenerics().pushGenericType(genericTypes[0]);
      if (keySerializer != null) {
        key = kryo.readObject(input, keyType, keySerializer);
      } else
        key = kryo.readClassAndObject(input);
      if (genericTypes != null) kryo.getGenerics().popGenericType();
      Object value;
      if (valueSerializer != null) {
        value = kryo.readObject(input, valueType, valueSerializer);
      } else
        value = kryo.readClassAndObject(input);
      builder.put(key, value);
    }
    kryo.getGenerics().popGenericType();
    T map = (T) builder.build();
    kryo.reference(map);
    return map;
  }

  @Override
  @SuppressWarnings({"unchecked", "UnstableApiUsage"})
  public T copy(Kryo kryo, T original) {
    if (original == null) {
      return null;
    }
    ImmutableMap.Builder<Object, Object> builder;
    if (original instanceof ImmutableSortedMap) {
      Comparator<Object> comparator = ((ImmutableSortedMap<Object, Object>) original).comparator();
      builder = ImmutableSortedMap.orderedBy(comparator);
    } else if (original instanceof ImmutableBiMap) {
      builder = ImmutableBiMap.builderWithExpectedSize(original.size());
    } else {
      builder = ImmutableMap.builderWithExpectedSize(original.size());
    }
    for (Map.Entry<?, ?> entry : original.entrySet()) {
      builder.put(kryo.copy(entry.getKey()), kryo.copy(entry.getValue()));
    }
    return (T) builder.build();
  }
}
