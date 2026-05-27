package io.vertxrpc0.kryo.serializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.Ordering;
import io.vertxrpc0.Comparators;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

public class ComparatorSerializerTest {

  private final Kryo safeKryo = newKryo(false);
  private final Kryo unsafeKryo = newKryo(true);

  private static Kryo newKryo(boolean trustUnsafe) {
    return new Kryo() {
      {
        register(Comparator.class, new ComparatorSerializer(trustUnsafe));
      }

      @Override
      public Registration getRegistration(Class type) {
        if (Comparator.class.isAssignableFrom(type)) {
          return super.getRegistration(Comparator.class);
        }
        return super.getRegistration(type);
      }
    };
  }

  @SneakyThrows
  @SuppressWarnings("unchecked")
  private static <T> T roundtrip(Kryo kryo, T value) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (Output output = new Output(out)) {
      kryo.writeClassAndObject(output, value);
    }
    try (Input input = new Input(out.toByteArray())) {
      return (T) kryo.readClassAndObject(input);
    }
  }

  // ---------------------------------------------------------------------------
  // The pre-existing JDK/Guava singleton tags (1..6) still work in safe mode.
  // ---------------------------------------------------------------------------

  private static <T> void sortsTheSame(Comparator<? super T> expected,
                                       Comparator<?> actual,
                                       List<T> sample) {
    @SuppressWarnings("unchecked")
    Comparator<? super T> coerced = (Comparator<? super T>) actual;
    List<T> a = new ArrayList<>(sample);
    List<T> b = new ArrayList<>(sample);
    a.sort(expected);
    b.sort(coerced);
    assertEquals(a, b, "round-tripped comparator must order the sample identically");
  }

  // ---------------------------------------------------------------------------
  // Safe-mode rejection — out-of-scope inputs throw, no silent null.
  // ---------------------------------------------------------------------------

  @Test
  public void verifyStaticSingletonsRoundTripInSafeMode() {
    assertEquals(Comparator.naturalOrder(), roundtrip(safeKryo, Comparator.naturalOrder()));
    assertEquals(Comparator.reverseOrder(), roundtrip(safeKryo, Comparator.reverseOrder()));
    assertEquals(Ordering.natural(), roundtrip(safeKryo, Ordering.natural()));
    assertEquals(Ordering.allEqual(), roundtrip(safeKryo, Ordering.allEqual()));
    assertEquals(Ordering.arbitrary(), roundtrip(safeKryo, Ordering.arbitrary()));
    assertEquals(Ordering.usingToString(), roundtrip(safeKryo, Ordering.usingToString()));
    assertNull(roundtrip(safeKryo, (Comparator<?>) null));
  }

  @Test
  public void safeModeRejectsExplicitOrdering() {
    Comparator<Integer> explicit = Ordering.explicit(1, 2, 3, 4, 5, 6);
    assertThrows(KryoException.class, () -> roundtrip(safeKryo, explicit));
  }

  @Test
  public void safeModeRejectsLambdaFromComparingInt() {
    ToIntFunction<? super String> func = (ToIntFunction<? super String> & Serializable) String::length;
    Comparator<String> lambda = Comparator.comparingInt(func);
    assertThrows(KryoException.class, () -> roundtrip(safeKryo, lambda));
  }

  @Test
  public void safeModeRejectsAnonymousComparator() {
    Comparator<String> anon = new Comparator<>() {
      @Override
      public int compare(String o1, String o2) {
        return 0;
      }
    };
    assertThrows(KryoException.class, () -> roundtrip(safeKryo, anon));
  }

  // ---------------------------------------------------------------------------
  // The trustUnsafe escape hatch keeps working for Serializable inputs.
  // ---------------------------------------------------------------------------

  /**
   * Hostile captured-lambda payload must never reach the deserializer in safe mode —
   * the body must not even have a chance to execute.
   */
  @Test
  public void safeModeRejectsHostileCapturedLambda() {
    AtomicInteger sideEffect = new AtomicInteger();
    ToIntFunction<? super String> func = (ToIntFunction<? super String> & Serializable) s -> {
      sideEffect.incrementAndGet();
      return s.length();
    };
    Comparator<String> hostile = Comparator.comparingInt(func);
    assertThrows(KryoException.class, () -> roundtrip(safeKryo, hostile));
    assertEquals(0, sideEffect.get());
  }

  @Test
  public void unsafeModeRoundTripsSerializableLambda() {
    ToIntFunction<? super String> func = (ToIntFunction<? super String> & Serializable) String::length;
    Comparator<String> lambda = Comparator.comparingInt(func);
    Comparator<?> back = roundtrip(unsafeKryo, lambda);
    assertNotNull(back);
    sortsTheSame(lambda, back, List.of("aaa", "b", "cc"));
  }

  @Test
  public void unsafeModeRoundTripsExplicitOrdering() {
    Comparator<Integer> explicit = Ordering.explicit(3, 1, 4, 5, 9);
    Comparator<?> back = roundtrip(unsafeKryo, explicit);
    assertNotNull(back);
    assertEquals(explicit, back);
  }

  // ---------------------------------------------------------------------------
  // New chainable Comparators API — safe in both modes.
  // ---------------------------------------------------------------------------

  @Test
  public void unsafeModeStillRejectsNonSerializableAnonymous() {
    Comparator<String> anon = new Comparator<>() {
      @Override
      public int compare(String o1, String o2) {
        return 0;
      }
    };
    assertThrows(KryoException.class, () -> roundtrip(unsafeKryo, anon));
  }

  @Test
  public void chainableLeafSingletonsRoundTripWithIdentityPreserved() {
    assertSame(Comparators.natural(),       roundtrip(safeKryo, Comparators.natural()));
    assertSame(Comparators.reverseOrder(),  roundtrip(safeKryo, Comparators.reverseOrder()));
    assertSame(Comparators.usingToString(), roundtrip(safeKryo, Comparators.usingToString()));
    assertSame(Comparators.allEqual(),      roundtrip(safeKryo, Comparators.allEqual()));
    assertSame(Comparators.arbitrary(),     roundtrip(safeKryo, Comparators.arbitrary()));
  }

  @Test
  public void chainableNullsFirstRoundTrip() {
    Comparators<Integer> nf = Comparators.<Integer>natural().nullsFirst();
    assertEquals(nf, roundtrip(safeKryo, nf));
    sortsTheSame(nf, roundtrip(safeKryo, nf), Arrays.asList(3, null, 1, 2, null));
  }

  @Test
  public void chainableNullsLastRoundTrip() {
    Comparators<Integer> nl = Comparators.<Integer>natural().nullsLast();
    assertEquals(nl, roundtrip(safeKryo, nl));
    sortsTheSame(nl, roundtrip(safeKryo, nl), Arrays.asList(3, null, 1, 2, null));
  }

  @Test
  public void chainableReversedRoundTrip() {
    Comparators<Integer> r = Comparators.<Integer>natural().nullsFirst().reverse();
    assertEquals(r, roundtrip(safeKryo, r));
    sortsTheSame(r, roundtrip(safeKryo, r), Arrays.asList(3, null, 1, 2));
  }

  @Test
  public void chainableCompoundRoundTrip() {
    Comparators<Integer> c = Comparators.<Integer>natural()
            .compound(Comparators.<Integer>reverseOrder());
    assertEquals(c, roundtrip(safeKryo, c));
    sortsTheSame(c, roundtrip(safeKryo, c), List.of(10, 2, 33, 4, 5));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  @Test
  public void chainableNestedRoundTrip() {
    Comparators<Integer> nested = Comparators.<Integer>natural()
            .compound(Comparators.<Integer>reverseOrder())
            .reverse()
            .nullsFirst();
    assertEquals(nested, roundtrip(safeKryo, nested));
    sortsTheSame(nested, roundtrip(safeKryo, nested), Arrays.asList(3, null, 1, 2));
  }
}
