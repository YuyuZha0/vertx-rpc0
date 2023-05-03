package com.github.rpc0.kryo;

import com.google.common.collect.MapMaker;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author fishzhao
 * @since 2022-02-24
 */
public class MapMakerTest {

  private final Map<Integer, Double> concurrentMap = new MapMaker()
          .concurrencyLevel(3).weakKeys().makeMap();
//private final Map<Integer, Double> concurrentMap = new WeakHashMap<>();

  @Test
  @Ignore
  public void test() throws Exception {
    for (int i = 0; i < 10; ++i) {
      for (int j = 0; j < 10; ++j) {
        if (j == 5) {
          System.gc();
          TimeUnit.MILLISECONDS.sleep(200);
          System.out.println("invoke gc");
        }
        System.out.println(i + ": " + getOrCreateDouble(i + 99999) + "@" + concurrentMap.size());
      }
    }
    System.gc();
    TimeUnit.MILLISECONDS.sleep(2000);
    System.out.println(concurrentMap.size());
  }

  private Double getOrCreateDouble(int key) {
    return concurrentMap.computeIfAbsent(key, k -> Math.random());
  }
}
