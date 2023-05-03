package com.github.rpc0.invoke;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterators;
import com.google.common.primitives.Primitives;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.lang.invoke.MethodType;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.RandomAccess;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author fishzhao
 * @since 2021-12-22
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ParameterArray extends AbstractList<Object> implements RandomAccess, Serializable {

  private static final ParameterArray EMPTY = new ParameterArray(new Object[0]);
  private static final long serialVersionUID = -4941793371869143569L;

  private final Object[] parameters;

  public static ParameterArray create() {
    return EMPTY;
  }

  public static ParameterArray create(Object[] parameters, UnaryOperator<Object> transform) {
    if (parameters == null || parameters.length == 0) {
      return EMPTY;
    }
    if (transform == null) {
      return new ParameterArray(Arrays.copyOf(parameters, parameters.length));
    } else {
      Object[] transformed = new Object[parameters.length];
      for (int i = 0; i < transformed.length; ++i) {
        transformed[i] = transform.apply(parameters[i]);
      }
      return new ParameterArray(transformed);
    }
  }

  public static ParameterArray create(Object[] parameters) {
    return create(parameters, null);
  }

  public static ParameterArray create(Collection<?> collection, UnaryOperator<Object> transform) {
    if (collection == null || collection.isEmpty()) {
      return EMPTY;
    }
    if (transform == null) {
      return new ParameterArray(collection.toArray());
    } else {
      return new ParameterArray(collection.stream().map(transform).toArray());
    }
  }

  public static ParameterArray create(Collection<?> collection) {
    return create(collection, null);
  }

  private static String paramStr(Object o) {
    return o != null
            ? Strings.lenientFormat(
            "<%s:%s>",
            o, o.getClass().getSimpleName()
    ) : "null";
  }

  private static boolean notMatch(Object o, Class<?> type) {
    return o != null && !Primitives.wrap(type).isInstance(o);
  }

  @Override
  public int size() {
    return parameters.length;
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public Iterator<Object> iterator() {
    return Iterators.forArray(parameters);
  }

  @Override
  public Object[] toArray() {
    return Arrays.copyOf(parameters, parameters.length);
  }

  public boolean isTypeMatch(@NonNull MethodType methodType) {
    if (methodType.parameterCount() != size()) {
      return false;
    }
    for (int i = 0; i < parameters.length; ++i) {
      Object p = get(i);
      if (notMatch(p, methodType.parameterType(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof List)) return false;
    List<?> otherList = (List<?>) o;
    int size = size();
    if (size != otherList.size()) {
      return false;
    }
    if (otherList instanceof RandomAccess) {
      // avoid allocation and use the faster loop
      for (int i = 0; i < size; i++) {
        if (!Objects.equal(get(i), otherList.get(i))) {
          return false;
        }
      }
      return true;
    } else {
      return Iterators.elementsEqual(iterator(), otherList.iterator());
    }
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(parameters);
  }

  @Override
  public String toString() {
    return Arrays.stream(parameters).map(ParameterArray::paramStr).collect(Collectors.joining(", ", "[", "]"));
  }

  @Override
  public Object get(int index) {
    Preconditions.checkElementIndex(index, size());
    return parameters[index];
  }

  @Override
  public void replaceAll(UnaryOperator<Object> operator) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void sort(Comparator<? super Object> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Spliterator<Object> spliterator() {
    return Arrays.spliterator(parameters);
  }

  @Override
  public <T> T[] toArray(IntFunction<T[]> generator) {
    Preconditions.checkNotNull(generator, "generator");
    T[] a = generator.apply(size());
    System.arraycopy(parameters, 0, a, 0, a.length);
    return a;
  }

  @Override
  public boolean removeIf(Predicate<? super Object> filter) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Stream<Object> stream() {
    return Arrays.stream(parameters);
  }

  @Override
  public Stream<Object> parallelStream() {
    return StreamSupport.stream(spliterator(), true);
  }

  @Override
  public void forEach(Consumer<? super Object> action) {
    Preconditions.checkNotNull(action, "action");
    for (Object o : parameters) {
      action.accept(o);
    }
  }
}
