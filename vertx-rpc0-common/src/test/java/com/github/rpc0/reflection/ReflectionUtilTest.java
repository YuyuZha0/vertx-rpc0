package com.github.rpc0.reflection;

import io.vertx.core.Future;
import org.junit.Test;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ReflectionUtilTest {

  private static Future<Double> future() {
    return Future.succeededFuture();
  }

  private static Future<List<String>> future1() {
    return Future.succeededFuture();
  }

  private static List<String> list() {
    return null;
  }

  private static Map<Integer, Object[]> map() {
    return null;
  }


  @Test
  public void testFutureType() throws Exception {
    Type genericType = getClass()
            .getDeclaredMethod("future")
            .getGenericReturnType();
    Class<?> type = ReflectionUtil.getFutureResultType(genericType);
    assertEquals(Double.class, type);
  }

  @Test
  public void testFutureType1() throws Exception {
    Type genericType = getClass()
            .getDeclaredMethod("future1")
            .getGenericReturnType();
    Class<?> type = ReflectionUtil.getFutureResultType(genericType);
    assertEquals(List.class, type);
  }

  @Test
  public void testCollectionType() throws Exception {
    Type genericType = getClass()
            .getDeclaredMethod("list")
            .getGenericReturnType();
    Class<?> type = ReflectionUtil.getCollectionElementType(genericType);
    assertEquals(String.class, type);
  }

  @Test
  public void testMapType() throws Exception {
    Type genericType = getClass()
            .getDeclaredMethod("map")
            .getGenericReturnType();
    Map.Entry<Class<?>, Class<?>> entry = ReflectionUtil.getMapKeyValueType(genericType);
    assertEquals(Integer.class, entry.getKey());
    assertEquals(Object[].class, entry.getValue());
  }
}