package io.vertxrpc0.conf;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Registration;
import io.vertxrpc0.annotation.TrustedType;
import io.vertxrpc0.kryo.KryoRegistry;
import io.vertxrpc0.kryo.TrustedTypeKryoRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractConfiguratorTest {

  @Test
  public void packageScanRegistersAnnotatedNestedClasses() {
    TestConfigurator configurator = new TestConfigurator();
    configurator.registerTypes(AbstractConfiguratorTest.class.getPackageName(), false);

    KryoRegistry registry = configurator.getKryoRegistry();
    assertTrue(registry instanceof TrustedTypeKryoRegistry);

    Kryo kryo = new Kryo();
    int before = kryo.getNextRegistrationId();
    registry.registerClasses(kryo);

    Registration registration = kryo.getRegistration(TestBean.class);
    assertNotNull(registration);
    assertEquals(before + 2, registration.getId(),
            "typeId 1 should map to next-id + 1 + 1 per TrustedTypeKryoRegistry");
  }

  @Test
  public void registerTypeRejectsUnannotatedClass() {
    TestConfigurator configurator = new TestConfigurator();
    assertThrows(IllegalArgumentException.class, () -> configurator.registerType(String.class));
  }

  @Test
  public void registerTypeRejectsInterface() {
    TestConfigurator configurator = new TestConfigurator();
    assertThrows(IllegalArgumentException.class, () -> configurator.registerType(Runnable.class, 1));
  }

  @Test
  public void registerTypeRejectsNegativeId() {
    TestConfigurator configurator = new TestConfigurator();
    assertThrows(IllegalArgumentException.class, () -> configurator.registerType(TestBean.class, -1));
  }

  @Test
  public void registerTypeRejectsDuplicateIdForDifferentTypes() {
    TestConfigurator configurator = new TestConfigurator();
    configurator.registerType(TestBean.class, 1);
    assertThrows(IllegalArgumentException.class, () -> configurator.registerType(OtherBean.class, 1));
  }

  @Test
  public void registerTypeAllowsSameTypeRegisteredTwice() {
    TestConfigurator configurator = new TestConfigurator();
    configurator.registerType(TestBean.class, 1);
    configurator.registerType(TestBean.class, 1);
  }

  @Test
  public void customKryoRegistryReplacesDefault() {
    TestConfigurator configurator = new TestConfigurator();
    KryoRegistry custom = kryo -> {};
    configurator.setKryoRegistry(custom);
    assertFalse(configurator.getKryoRegistry() instanceof TrustedTypeKryoRegistry);
    assertEquals(custom, configurator.getKryoRegistry());
  }

  private static final class TestConfigurator extends AbstractConfigurator<TestConfigurator> {
    TestConfigurator() {
      super(Thread.currentThread().getContextClassLoader());
    }
  }

  @TrustedType(typeId = 1)
  public static final class TestBean {
  }

  @TrustedType(typeId = 2)
  public static final class OtherBean {
  }
}
