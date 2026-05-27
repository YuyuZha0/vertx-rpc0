package io.vertxrpc0.kryo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.util.IntMap;
import io.vertxrpc0.annotation.TrustedType;
import org.junit.jupiter.api.Test;

public class TrustedTypeKryoRegistryTest {

  @Test
  public void emptyMapIsNoOp() {
    Kryo kryo = new Kryo();
    int before = kryo.getNextRegistrationId();
    new TrustedTypeKryoRegistry().registerClasses(kryo);
    assertEquals(before, kryo.getNextRegistrationId());
  }

  @Test
  public void registerOffsetsByNextRegistrationIdPlusOne() {
    Kryo kryo = new Kryo();
    int baseline = kryo.getNextRegistrationId();

    IntMap<Class<?>> map = new IntMap<>();
    map.put(5, TypeA.class);
    new TrustedTypeKryoRegistry(map).registerClasses(kryo);

    Registration r = kryo.getRegistration(TypeA.class);
    assertNotNull(r);
    assertEquals(baseline + 1 + 5, r.getId());
  }

  @Test
  public void multipleTypesAreRegistered() {
    Kryo kryo = new Kryo();
    IntMap<Class<?>> map = new IntMap<>();
    map.put(5, TypeA.class);
    map.put(7, TypeB.class);

    new TrustedTypeKryoRegistry(map).registerClasses(kryo);
    assertNotNull(kryo.getRegistration(TypeA.class));
    assertNotNull(kryo.getRegistration(TypeB.class));
  }

  @Test
  public void constructorRejectsNullMap() {
    assertThrows(NullPointerException.class, () -> new TrustedTypeKryoRegistry(null));
  }

  @TrustedType(typeId = 5)
  public static final class TypeA {}

  @TrustedType(typeId = 7)
  public static final class TypeB {}
}
