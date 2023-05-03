package com.github.rpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ImmutableSerializer;
import com.esotericsoftware.kryo.util.ObjectIntMap;
import com.esotericsoftware.kryo.util.ObjectMap;
import com.esotericsoftware.minlog.Log;
import com.google.common.collect.Ordering;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.Comparator;

/**
 * @author fishzhao
 * @since 2022-01-23
 */
public final class ComparatorSerializer extends ImmutableSerializer<Comparator<?>> {

  private static final ObjectIntMap<Class<?>> SUPPORTED = new ObjectIntMap<>();
  private static final int USING_JDK = 100;

  static {
    SUPPORTED.put(Comparator.naturalOrder().getClass(), 1);
    SUPPORTED.put(Comparator.reverseOrder().getClass(), 2);
    SUPPORTED.put(Ordering.natural().getClass(), 3);
    SUPPORTED.put(Ordering.allEqual().getClass(), 4);
    SUPPORTED.put(Ordering.arbitrary().getClass(), 5);
    SUPPORTED.put(Ordering.usingToString().getClass(), 6);
  }

  private final boolean trustUnsafe;

  {
    setAcceptsNull(true);
  }

  public ComparatorSerializer(boolean trustUnsafe) {
    this.trustUnsafe = trustUnsafe;
  }

  public ComparatorSerializer() {
    this(false);
  }

  private boolean isSafeUsingJdkSerialization(Comparator<?> comparator) {
    return trustUnsafe && comparator instanceof Serializable;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void write(Kryo kryo, Output output, Comparator<?> comparator) {
    if (comparator == null) {
      output.writeByte(0);
      return;
    }
    int id = SUPPORTED.get(comparator.getClass(), -1);
    if (id > 0) {
      output.writeInt(id + 1, true);
    } else if (isSafeUsingJdkSerialization(comparator)) { // may cause vulnerability: baeldung.com/java-deserialization-vulnerabilities
      output.writeInt(USING_JDK + 1, true);
      try {
        ObjectMap<Object, Object> graphContext = kryo.getGraphContext();
        ObjectOutputStream objectStream = (ObjectOutputStream) graphContext.get(this);
        if (objectStream == null) {
          objectStream = new ObjectOutputStream(output);
          graphContext.put(this, objectStream);
        }
        objectStream.writeObject(comparator);
        objectStream.flush();
      } catch (Exception ex) {
        throw new KryoException("Error during Java serialization.", ex);
      }
    } else {
      output.writeInt(1, true);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Comparator<?> read(Kryo kryo, Input input, Class<? extends Comparator<?>> type) {
    int id = input.readInt(true) - 1;
    switch (id) {
      case 1:
        return Comparator.naturalOrder();
      case 2:
        return Comparator.reverseOrder();
      case 3:
        return Ordering.natural();
      case 4:
        return Ordering.allEqual();
      case 5:
        return Ordering.arbitrary();
      case 6:
        return Ordering.usingToString();
      case USING_JDK: {
        if (!trustUnsafe) {
          throw new KryoException("Deserialization of unsafe comparator is prohibited!");
        }
        try {
          ObjectMap<Object, Object> graphContext = kryo.getGraphContext();
          ObjectInputStream objectStream = (ObjectInputStream) graphContext.get(this);
          if (objectStream == null) {
            objectStream = new ObjectInputStreamWithKryoClassLoader(input, kryo);
            graphContext.put(this, objectStream);
          }
          return (Comparator<?>) objectStream.readObject();
        } catch (Exception ex) {
          throw new KryoException("Error during Java deserialization.", ex);
        }
      }
      default:
        return null;
    }
  }


  /**
   * {@link ObjectInputStream} uses the last user-defined {@link ClassLoader}, which may not be the correct one. This is a known
   * Java issue and is often solved by using a specific class loader. See:
   * https://github.com/apache/spark/blob/v1.6.3/streaming/src/main/scala/org/apache/spark/streaming/Checkpoint.scala#L154
   * https://issues.apache.org/jira/browse/GROOVY-1627
   */
  private static class ObjectInputStreamWithKryoClassLoader extends ObjectInputStream {
    private final Kryo kryo;

    ObjectInputStreamWithKryoClassLoader(InputStream in, Kryo kryo) throws IOException {
      super(in);
      this.kryo = kryo;
    }

    protected Class<?> resolveClass(ObjectStreamClass type) {
      try {
        if (Log.TRACE) {
          Log.trace("Resolving class in comparator deserialization: " + type.getName());
        }
        return Class.forName(type.getName(), false, kryo.getClassLoader());
      } catch (ClassNotFoundException ex) {
        throw new KryoException("Class not found: " + type.getName(), ex);
      }
    }
  }
}
