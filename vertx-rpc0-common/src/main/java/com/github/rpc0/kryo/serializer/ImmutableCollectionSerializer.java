package com.github.rpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ImmutableSerializer;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import java.util.Comparator;
import java.util.SortedSet;

import static com.esotericsoftware.kryo.Kryo.NULL;

/**
 * @author fishzhao
 * @since 2022-02-17
 */
public final class ImmutableCollectionSerializer<T extends ImmutableCollection<?>> extends ImmutableSerializer<T> {

  private static final byte LIST = 1;
  private static final byte SET = 2;
  private static final byte SORTED_SET = 3;

  {
    setAcceptsNull(true);
  }

  private void writeComparator(Kryo kryo, Output output, T collection) {
    kryo.writeClassAndObject(output, ((SortedSet<?>) collection).comparator());
  }

  @Override
  public void write(Kryo kryo, Output output, T collection) {
    if (collection == null) {
      output.writeByte(NULL);
      return;
    }
    if (collection instanceof ImmutableList) {
      output.writeByte(LIST);
    } else if (collection instanceof ImmutableSortedSet) {
      output.writeByte(SORTED_SET);
      writeComparator(kryo, output, collection);
    } else if (collection instanceof ImmutableSet) {
      output.writeByte(SET);
    } else {
      throw new KryoException("Unsupported immutable collection type: " + collection.getClass().getTypeName());
    }
    int length = collection.size();
    output.writeVarInt(length + 1, true);
    if (length == 0) {
      return;
    }
    Class<?> elementType;
    Class<?> genericClass = kryo.getGenerics().nextGenericClass();
    if (genericClass != null && kryo.isFinal(genericClass)) {
      elementType = genericClass;
    } else {
      elementType = RuntimeTypeTrait.elementType(collection);
    }
    Serializer<?> serializer;
    if (elementType != null && (serializer = kryo.getSerializer(elementType)) != null) {
      kryo.writeClass(output, elementType);
      for (Object element : collection) {
        kryo.writeObject(output, element, serializer);
      }
    } else {
      kryo.writeClass(output, null);
      for (Object element : collection) {
        kryo.writeClassAndObject(output, element);
      }
    }
    kryo.getGenerics().popGenericType();
  }

  @Override
  @SuppressWarnings({"unchecked", "UnstableApiUsage"})
  public T read(Kryo kryo, Input input, Class<? extends T> type) {
    byte hint = input.readByte();
    if (hint == NULL) {
      return null;
    }
    Comparator<?> comparator = null;
    if (hint == SORTED_SET) {
      comparator = (Comparator<?>) kryo.readClassAndObject(input);
      if (comparator == null) {
        throw new KryoException("Null comparator!");
      }
    }
    int length = input.readVarInt(true) - 1;
    if (length == 0) {
      switch (hint) {
        case LIST:
          return (T) ImmutableList.of();
        case SORTED_SET:
          return (T) (ImmutableSortedSet.orderedBy(comparator).build());
        case SET:
          return (T) ImmutableSet.of();
        default:
          throw new KryoException("Unable to deserialize class: " + type);
      }
    }
    ImmutableCollection.Builder<Object> builder;
    switch (hint) {
      case LIST:
        builder = ImmutableList.builderWithExpectedSize(length);
        break;
      case SORTED_SET:
        builder = ImmutableSortedSet.orderedBy((Comparator<Object>) comparator);
        break;
      case SET:
        builder = ImmutableSet.builderWithExpectedSize(length);
        break;
      default:
        throw new KryoException("Unable to deserialize class: " + type);
    }
    Registration registration = kryo.readClass(input);
    if (registration != null) {
      Class<?> elementType = registration.getType();
      Serializer<?> elementSerializer = kryo.getSerializer(elementType);
      for (int i = 0; i < length; ++i) {
        builder.add(kryo.readObject(input, elementType, elementSerializer));
      }
    } else {
      for (int i = 0; i < length; ++i) {
        builder.add(kryo.readClassAndObject(input));
      }
    }
    T collection = (T) builder.build();
    kryo.reference(collection);
    return collection;
  }

  @Override
  @SuppressWarnings({"unchecked", "UnstableApiUsage"})
  public T copy(Kryo kryo, T original) {
    if (original == null) {
      return null;
    }
    ImmutableCollection.Builder<Object> builder;
    if (original instanceof ImmutableSortedSet) {
      Comparator<Object> comparator = ((ImmutableSortedSet<Object>) original).comparator();
      assert comparator != null;
      builder = ImmutableSortedSet.orderedBy(comparator);
    } else if (original instanceof ImmutableSet) {
      builder = ImmutableSet.builderWithExpectedSize(original.size());
    } else if (original instanceof ImmutableList) {
      builder = ImmutableList.builderWithExpectedSize(original.size());
    } else {
      throw new KryoException("Unable to copy collection: " + original.getClass());
    }
    for (Object element : original) {
      builder.add(kryo.copy(element));
    }
    return (T) builder.build();
  }
}
