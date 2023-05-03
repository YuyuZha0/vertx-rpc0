package com.github.rpc0.kryo;

import com.esotericsoftware.kryo.ClassResolver;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.ReferenceResolver;
import com.esotericsoftware.kryo.Registration;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.vertx.core.buffer.Buffer;
import lombok.NonNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

/**
 * @author fishzhao
 * @since 2022-01-21
 */
public final class SafeKryo extends Kryo {


  private static final List<Class<?>> COLLECTION_TYPES = ImmutableList.of(
          ImmutableCollection.class,
          List.class,
          SortedSet.class,
          Set.class,
          Deque.class,
          Queue.class
  );

  private static final List<Class<?>> MAP_TYPES = ImmutableList.of(
          ImmutableMap.class,
          SortedMap.class
  );

  public SafeKryo() {
    super(new TrustedReferenceResolver());
  }

  public SafeKryo(ReferenceResolver referenceResolver) {
    super(referenceResolver);
  }

  public SafeKryo(ClassResolver classResolver, ReferenceResolver referenceResolver) {
    super(classResolver, referenceResolver);
  }

  @Override
  public Registration getRegistration(@NonNull Class type) {
    ClassResolver classResolver = getClassResolver();
    Registration registration = classResolver.getRegistration(type);
    if (registration != null) {
      return registration;
    }
    if (type.isArray()) {
      return classResolver.getRegistration(Object[].class);
    }
    if (Collection.class.isAssignableFrom(type)) {
      for (Class<?> collectionType : COLLECTION_TYPES) {
        if (collectionType.isAssignableFrom(type)) {
          return classResolver.getRegistration(collectionType);
        }
      }
      return classResolver.getRegistration(Collection.class);
    }
    if (Map.class.isAssignableFrom(type)) {
      for (Class<?> mapType : MAP_TYPES) {
        if (mapType.isAssignableFrom(type)) {
          return classResolver.getRegistration(mapType);
        }
      }
      return classResolver.getRegistration(Map.class);
    }
    if (Comparator.class.isAssignableFrom(type)) {
      return classResolver.getRegistration(Comparator.class);
    }
    if (Buffer.class.isAssignableFrom(type)) {
      return classResolver.getRegistration(Buffer.class);
    }
    if (!type.isEnum() && Enum.class.isAssignableFrom(type) && type != Enum.class) {
      // This handles an enum value that is an inner class, eg: enum A {b{}}
      while (true) {
        type = type.getSuperclass();
        if (type == null) break;
        if (type.isEnum()) {
          return classResolver.getRegistration(type);
        }
      }
    } else if (EnumSet.class.isAssignableFrom(type)) {
      return classResolver.getRegistration(EnumSet.class);
    }
    throw new IllegalArgumentException(unregisteredClassMessage(type));
  }

  @Override
  public void setRegistrationRequired(boolean registrationRequired) {
    throw new KryoException("Auto registration prohibited!");
  }
}
