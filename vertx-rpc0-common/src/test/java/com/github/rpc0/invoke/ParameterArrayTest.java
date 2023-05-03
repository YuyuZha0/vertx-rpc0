package com.github.rpc0.invoke;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ParameterArrayTest {

  @Test
  public void test() {
    List<Object> a = Arrays.asList(
            1,
            Math.PI,
            null,
            "hello",
            675L,
            ImmutableMap.of(),
            UUID.randomUUID()
    );
    ParameterArray b = ParameterArray.create(a);

    assertEquals(a, b);
    assertEquals(b, a);

    assertArrayEquals(a.toArray(), b.toArray());
    assertArrayEquals(a.toArray(new Object[0]), b.toArray(Object[]::new));

    assertTrue(Iterators.elementsEqual(
            a.iterator(),
            b.iterator()
    ));

    assertEquals(a.subList(0, 3), b.subList(0, 3));

    assertEquals(Collections.emptyList(), ParameterArray.create());
    assertEquals(Collections.singletonList(1), ParameterArray.create(new Object[]{1}));
    assertEquals(new ArrayList<>(b), b);
  }

}