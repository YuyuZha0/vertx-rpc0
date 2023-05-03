package com.github.rpc0.perf;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.github.rpc0.kryo.KryoFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * <pre>
 *   Benchmark                    Mode  Cnt  Score   Error  Units
 * TypeQueryBenchmark.def       avgt   25  6.117 ± 0.398  ns/op
 * TypeQueryBenchmark.identity  avgt   25  5.457 ± 0.314  ns/op
 * </pre>
 *
 * @author fishzhao
 * @since 2022-01-23
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class TypeQueryBenchmark {

  private final Set<Class<?>> def = KryoFactory.getValueTypes();
  private final Set<Class<?>> identity = Sets.newIdentityHashSet();

  private Class<?>[] a;
  private int index;

  @Setup
  public void setup() {
    identity.addAll(def);

    a = Stream.of(
            KryoFactory.getValueTypes(),
            ImmutableSet.of(Object.class, Void.class)
    ).flatMap(Set::stream).toArray(Class<?>[]::new);
    index = 0;
  }

  @Benchmark
  public boolean def() {
    if (index >= a.length) {
      index = 0;
    }
    return def.contains(a[index++]);
  }

  @Benchmark
  public boolean identity() {
    if (index >= a.length) {
      index = 0;
    }
    return identity.contains(a[index++]);
  }
}
