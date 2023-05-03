package com.github.rpc0.conf;

import com.github.rpc0.annotation.TrustedType;
import com.github.rpc0.kryo.TrustedTypeKryoRegistry;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AbstractConfiguratorTest {

  @Test
  public void test() {
    TestConfigurator configurator = new TestConfigurator();
    configurator.registerTypes("com.tencent.rpc0.reflection", false);
    assertTrue(configurator.getKryoRegistry() instanceof TrustedTypeKryoRegistry);
    configurator.setKryoRegistry(kryo -> {
    });
    assertFalse(configurator.getKryoRegistry() instanceof TrustedTypeKryoRegistry);
  }

  private static final class TestConfigurator extends AbstractConfigurator<TestConfigurator> {
    public TestConfigurator() {
      super(Thread.currentThread().getContextClassLoader());
    }
  }

  @TrustedType(typeId = 1)
  public static final class TestBean {

  }
}