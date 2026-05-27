package io.vertxrpc0;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

/**
 * Wire-safe, chainable {@link Comparator} family modeled after Guava's {@code Ordering}. Every
 * subclass here is wire-representable through {@code ComparatorSerializer} without any reflection
 * on JDK or Guava internals and without the JDK-deserialization attack surface that the {@code
 * trustUnsafe} fallback opens.
 *
 * <p>Use the static factories ({@link #natural()}, {@link #reverseOrder()}, {@link
 * #usingToString()}, {@link #allEqual()}, {@link #arbitrary()}) to obtain leaf singletons, and
 * chain methods ({@link #reverse()}, {@link #nullsFirst()}, {@link #nullsLast()}, {@link
 * #compound(Comparators)}) to compose them. Anything passed to {@code compound} is itself a {@code
 * Comparators}, so the entire chain is statically guaranteed to be wire-representable.
 *
 * <p>Each concrete subclass owns its magic number on the wire ({@link #magic()}). The number space
 * is local to this class — outer serializers never see it. Adding a new subclass: declare a new
 * {@code MAGIC} constant, override {@link #magic()}, {@link #writeBody(Kryo, Output)}, and the
 * {@code compare} method, expose a static {@code decode} (for wrappers; leaves return the singleton
 * directly), add the class to the {@code permits} list, and add a {@code case} in {@link
 * #readFrom(Kryo, Input)}. The outer serializer never changes.
 */
public abstract sealed class Comparators<T> implements Comparator<T>
    permits Comparators.Natural,
        Comparators.ReverseOrder,
        Comparators.UsingToString,
        Comparators.AllEqual,
        Comparators.Arbitrary,
        Comparators.Reversed,
        Comparators.NullsFirst,
        Comparators.NullsLast,
        Comparators.Compound {

  protected Comparators() {}

  /**
   * Public entry point used by {@code ComparatorSerializer}. Reads magic + state and returns a
   * fully-constructed instance.
   */
  public static Comparators<?> readFrom(@NonNull Kryo kryo, @NonNull Input input) {
    int magic = input.readVarInt(true);
    return switch (magic) {
      case Natural.MAGIC -> Natural.INSTANCE;
      case ReverseOrder.MAGIC -> ReverseOrder.INSTANCE;
      case UsingToString.MAGIC -> UsingToString.INSTANCE;
      case AllEqual.MAGIC -> AllEqual.INSTANCE;
      case Arbitrary.MAGIC -> Arbitrary.INSTANCE;
      case Reversed.MAGIC -> Reversed.decode(kryo, input);
      case NullsFirst.MAGIC -> NullsFirst.decode(kryo, input);
      case NullsLast.MAGIC -> NullsLast.decode(kryo, input);
      case Compound.MAGIC -> Compound.decode(kryo, input);
      default -> throw new KryoException("Unknown Comparators magic: " + magic);
    };
  }

  public static <T extends Comparable<? super T>> Comparators<T> natural() {
    return Natural.cast();
  }

  public static <T> Comparators<T> reverseOrder() {
    return ReverseOrder.cast();
  }

  public static <T> Comparators<T> usingToString() {
    return UsingToString.cast();
  }

  // === Public API: leaf factories ===

  public static <T> Comparators<T> allEqual() {
    return AllEqual.cast();
  }

  public static <T> Comparators<T> arbitrary() {
    return Arbitrary.cast();
  }

  /** The wire magic identifying this subclass. */
  protected abstract int magic();

  /** Subclass writes only its state. The magic is written by {@link #writeTo}. */
  protected abstract void writeBody(Kryo kryo, Output output);

  /** Public entry point used by {@code ComparatorSerializer}. Emits magic + state. */
  public final void writeTo(@NonNull Kryo kryo, @NonNull Output output) {
    output.writeVarInt(magic(), true);
    writeBody(kryo, output);
  }

  // === Public API: chain ops ===

  public Comparators<T> reverse() {
    return new Reversed<>(this);
  }

  public Comparators<T> nullsFirst() {
    return new NullsFirst<>(this);
  }

  public Comparators<T> nullsLast() {
    return new NullsLast<>(this);
  }

  public Comparators<T> compound(@NonNull Comparators<? super T> next) {
    return new Compound<>(List.of(this, next));
  }

  // ===========================================================================
  // Leaves — stateless singletons.
  // ===========================================================================

  public static final class Natural<T extends Comparable<? super T>> extends Comparators<T> {
    static final int MAGIC = 10;
    static final Natural<?> INSTANCE = new Natural<>();

    private Natural() {}

    @SuppressWarnings("unchecked")
    static <T extends Comparable<? super T>> Natural<T> cast() {
      return (Natural<T>) INSTANCE;
    }

    @Override
    public int compare(T a, T b) {
      return a.compareTo(b);
    }

    @Override
    protected int magic() {
      return MAGIC;
    }

    @Override
    protected void writeBody(Kryo kryo, Output output) {
      // no state
    }
  }

  public static final class ReverseOrder<T> extends Comparators<T> {
    static final int MAGIC = 11;
    static final ReverseOrder<?> INSTANCE = new ReverseOrder<>();

    private ReverseOrder() {}

    @SuppressWarnings("unchecked")
    static <T> ReverseOrder<T> cast() {
      return (ReverseOrder<T>) INSTANCE;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public int compare(T a, T b) {
      return ((Comparable) b).compareTo(a);
    }

    @Override
    protected int magic() {
      return MAGIC;
    }

    @Override
    protected void writeBody(Kryo kryo, Output output) {
      // no state
    }
  }

  public static final class UsingToString<T> extends Comparators<T> {
    static final int MAGIC = 12;
    static final UsingToString<?> INSTANCE = new UsingToString<>();

    private UsingToString() {}

    @SuppressWarnings("unchecked")
    static <T> UsingToString<T> cast() {
      return (UsingToString<T>) INSTANCE;
    }

    @Override
    public int compare(T a, T b) {
      return a.toString().compareTo(b.toString());
    }

    @Override
    protected int magic() {
      return MAGIC;
    }

    @Override
    protected void writeBody(Kryo kryo, Output output) {
      // no state
    }
  }

  public static final class AllEqual<T> extends Comparators<T> {
    static final int MAGIC = 13;
    static final AllEqual<?> INSTANCE = new AllEqual<>();

    private AllEqual() {}

    @SuppressWarnings("unchecked")
    static <T> AllEqual<T> cast() {
      return (AllEqual<T>) INSTANCE;
    }

    @Override
    public int compare(T a, T b) {
      return 0;
    }

    @Override
    protected int magic() {
      return MAGIC;
    }

    @Override
    protected void writeBody(Kryo kryo, Output output) {
      // no state
    }
  }

  public static final class Arbitrary<T> extends Comparators<T> {
    static final int MAGIC = 14;
    static final Arbitrary<?> INSTANCE = new Arbitrary<>();

    private Arbitrary() {}

    @SuppressWarnings("unchecked")
    static <T> Arbitrary<T> cast() {
      return (Arbitrary<T>) INSTANCE;
    }

    @Override
    public int compare(T a, T b) {
      if (a == b) return 0;
      int ha = System.identityHashCode(a);
      int hb = System.identityHashCode(b);
      // identityHashCode collisions are vanishingly rare in practice but
      // the contract requires a total order, so fall back to class name.
      int byHash = Integer.compare(ha, hb);
      if (byHash != 0) return byHash;
      return a.getClass().getName().compareTo(b.getClass().getName());
    }

    @Override
    protected int magic() {
      return MAGIC;
    }

    @Override
    protected void writeBody(Kryo kryo, Output output) {
      // no state
    }
  }

  // ===========================================================================
  // Wrappers — immutable, hold one or more inner Comparators.
  // ===========================================================================

  @EqualsAndHashCode(callSuper = false)
  @ToString
  public static final class Reversed<T> extends Comparators<T> {
    static final int MAGIC = 20;

    private final Comparators<? super T> inner;

    Reversed(@NonNull Comparators<? super T> inner) {
      this.inner = inner;
    }

    static Reversed<?> decode(Kryo kryo, Input input) {
      return new Reversed<>(Comparators.readFrom(kryo, input));
    }

    @SuppressWarnings("unchecked")
    @Override
    public int compare(T a, T b) {
      return ((Comparators<T>) inner).compare(b, a);
    }

    @Override
    protected int magic() {
      return MAGIC;
    }

    @Override
    protected void writeBody(Kryo kryo, Output output) {
      inner.writeTo(kryo, output);
    }
  }

  @EqualsAndHashCode(callSuper = false)
  @ToString
  public static final class NullsFirst<T> extends Comparators<T> {
    static final int MAGIC = 21;

    private final Comparators<? super T> inner;

    NullsFirst(@NonNull Comparators<? super T> inner) {
      this.inner = inner;
    }

    static NullsFirst<?> decode(Kryo kryo, Input input) {
      return new NullsFirst<>(Comparators.readFrom(kryo, input));
    }

    @SuppressWarnings("unchecked")
    @Override
    public int compare(T a, T b) {
      if (a == b) return 0;
      if (a == null) return -1;
      if (b == null) return 1;
      return ((Comparators<T>) inner).compare(a, b);
    }

    @Override
    protected int magic() {
      return MAGIC;
    }

    @Override
    protected void writeBody(Kryo kryo, Output output) {
      inner.writeTo(kryo, output);
    }
  }

  @EqualsAndHashCode(callSuper = false)
  @ToString
  public static final class NullsLast<T> extends Comparators<T> {
    static final int MAGIC = 22;

    private final Comparators<? super T> inner;

    NullsLast(@NonNull Comparators<? super T> inner) {
      this.inner = inner;
    }

    static NullsLast<?> decode(Kryo kryo, Input input) {
      return new NullsLast<>(Comparators.readFrom(kryo, input));
    }

    @SuppressWarnings("unchecked")
    @Override
    public int compare(T a, T b) {
      if (a == b) return 0;
      if (a == null) return 1;
      if (b == null) return -1;
      return ((Comparators<T>) inner).compare(a, b);
    }

    @Override
    protected int magic() {
      return MAGIC;
    }

    @Override
    protected void writeBody(Kryo kryo, Output output) {
      inner.writeTo(kryo, output);
    }
  }

  @EqualsAndHashCode(callSuper = false)
  @ToString
  public static final class Compound<T> extends Comparators<T> {
    static final int MAGIC = 32;

    private final List<Comparators<? super T>> inners;

    Compound(@NonNull List<? extends Comparators<? super T>> inners) {
      if (inners.isEmpty()) {
        throw new IllegalArgumentException("compound requires at least one inner");
      }
      this.inners = List.copyOf(inners);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Compound<?> decode(Kryo kryo, Input input) {
      int count = input.readVarInt(true);
      if (count <= 0) {
        throw new KryoException("Compound count must be > 0: " + count);
      }
      List<Comparators<?>> list = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        list.add(Comparators.readFrom(kryo, input));
      }
      return new Compound(list);
    }

    @SuppressWarnings("unchecked")
    @Override
    public int compare(T a, T b) {
      for (Comparators<? super T> c : inners) {
        int r = ((Comparators<T>) c).compare(a, b);
        if (r != 0) return r;
      }
      return 0;
    }

    @Override
    protected int magic() {
      return MAGIC;
    }

    @Override
    protected void writeBody(Kryo kryo, Output output) {
      output.writeVarInt(inners.size(), true);
      for (Comparators<? super T> inner : inners) {
        inner.writeTo(kryo, output);
      }
    }
  }
}
