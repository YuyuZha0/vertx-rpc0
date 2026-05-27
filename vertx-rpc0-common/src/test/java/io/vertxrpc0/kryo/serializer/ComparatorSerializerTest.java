package io.vertxrpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.minlog.Log;
import com.google.common.collect.Ordering;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ComparatorSerializerTest {

  static {
    Log.TRACE();
  }

  private final Kryo kryo = new Kryo() {

    {
      register(Comparator.class, new ComparatorSerializer(true));
    }

    @Override
    public Registration getRegistration(Class type) {
      if (Comparator.class.isAssignableFrom(type)) {
        return super.getRegistration(Comparator.class);
      }
      return super.getRegistration(type);
    }
  };

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
  public void verifyStatic() {
    assertEquals(
            Comparator.naturalOrder(),
            roundtrip(Comparator.naturalOrder())
    );
    assertEquals(
            Comparator.reverseOrder(),
            roundtrip(Comparator.reverseOrder())
    );
    assertEquals(
            Ordering.natural(),
            roundtrip(Ordering.natural())
    );
    assertEquals(Ordering.arbitrary(),
            roundtrip(Ordering.arbitrary()));
    assertEquals(
            Ordering.allEqual(),
            roundtrip(Ordering.allEqual())
    );
    assertEquals(
            Ordering.usingToString(),
            roundtrip(Ordering.usingToString())
    );
    assertNull(roundtrip(null));
  }

  @Test
  public void verifyDynamic() {
    Comparator<Integer> comparator1 = Ordering.natural().reverse();
    assertEquals(comparator1, roundtrip(comparator1));

    Comparator<Integer> comparator2 = Ordering.explicit(1, 2, 3, 4, 5, 6);
    assertEquals(comparator2, roundtrip(comparator2));

    ToIntFunction<? super String> func = (ToIntFunction<? super String> & Serializable) String::length;
    Comparator<String> comparator3 = Comparator.comparingInt(func);
    assertNotNull(roundtrip(comparator3));

    Comparator<String> comparator4 = new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        return 0;
      }
    };
    assertNull(roundtrip(comparator4));
  }

  /**
   * Untrusted captured-lambda payloads must not be deserialized into an executable comparator.
   * If a lambda with a side-effect-bearing body is reachable after a round-trip, that would mean
   * arbitrary code could be smuggled through the comparator stream.
   */
  @Test
  public void rejectsUntrustedCapturedLambda() {
    AtomicInteger sideEffect = new AtomicInteger();
    ToIntFunction<? super String> func = (ToIntFunction<? super String> & Serializable) s -> {
      sideEffect.incrementAndGet();
      return s.length();
    };
    Comparator<String> comparator = Comparator.comparingInt(func);

    Comparator<String> roundtrip = roundtrip(comparator);
    if (roundtrip != null) {
      roundtrip.compare("hello", "world");
    }
    assertEquals(0, sideEffect.get(),
            "untrusted captured-lambda payload must not execute its body after round-trip");
  }
}