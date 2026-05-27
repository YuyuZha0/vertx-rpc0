package io.vertxrpc0.kryo;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Registration;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

public class SafeKryoTest {

  @Test
  public void setRegistrationRequiredFalseIsRejected() {
    Kryo kryo = new SafeKryo();
    assertThrows(KryoException.class, () -> kryo.setRegistrationRequired(false));
  }

  @Test
  public void setRegistrationRequiredTrueIsNoOp() {
    Kryo kryo = new SafeKryo();
    kryo.setRegistrationRequired(true); // SafeKryo always requires registration
  }

  @Test
  public void unregisteredArbitraryClassRejected() {
    Kryo kryo = new SafeKryo();
    assertThrows(IllegalArgumentException.class, () -> kryo.getRegistration(SafeKryoTest.class));
  }

  @Test
  public void arrayDispatchesToObjectArrayRegistration() {
    Kryo kryo = new SafeKryo();
    Registration objArr = kryo.register(Object[].class);
    Registration found = kryo.getRegistration(SafeKryoTest[].class);
    assertSame(objArr, found);
  }

  @Test
  public void arrayListDispatchesToListRegistration() {
    Kryo kryo = new SafeKryo();
    Registration listReg = kryo.register(List.class);
    assertSame(listReg, kryo.getRegistration(java.util.ArrayList.class));
  }

  @Test
  public void immutableListDispatchesToImmutableCollectionRegistration() {
    Kryo kryo = new SafeKryo();
    Registration icReg = kryo.register(com.google.common.collect.ImmutableCollection.class);
    assertSame(icReg, kryo.getRegistration(ImmutableList.of(1).getClass()));
  }

  @Test
  public void sortedSetDispatchesToSortedSetRegistration() {
    Kryo kryo = new SafeKryo();
    Registration ssReg = kryo.register(SortedSet.class);
    assertSame(ssReg, kryo.getRegistration(TreeSet.class));
  }

  @Test
  public void unknownSetFallsBackToCollection() {
    Kryo kryo = new SafeKryo();
    kryo.register(java.util.Set.class);
    Registration colReg = kryo.register(Collection.class);
    Registration result = kryo.getRegistration(HashSet.class);
    assertNotNull(result);
    // HashSet implements Set first, then Collection; SafeKryo iterates COLLECTION_TYPES in order.
    assertTrue(result.getType() == java.util.Set.class || result == colReg);
  }

  @Test
  public void mapDispatchesToMapRegistration() {
    Kryo kryo = new SafeKryo();
    Registration mapReg = kryo.register(Map.class);
    assertSame(mapReg, kryo.getRegistration(HashMap.class));
  }

  @Test
  public void sortedMapDispatchesToSortedMapRegistration() {
    Kryo kryo = new SafeKryo();
    Registration smReg = kryo.register(SortedMap.class);
    assertSame(smReg, kryo.getRegistration(TreeMap.class));
  }

  @Test
  public void immutableMapDispatchesToImmutableMapRegistration() {
    Kryo kryo = new SafeKryo();
    Registration imReg = kryo.register(ImmutableMap.class);
    assertSame(imReg, kryo.getRegistration(ImmutableMap.of("k", "v").getClass()));
  }

  @Test
  public void comparatorDispatchesToComparatorRegistration() {
    Kryo kryo = new SafeKryo();
    Registration cmpReg = kryo.register(Comparator.class);
    Comparator<Integer> reversed = Comparator.<Integer>naturalOrder().reversed();
    assertSame(cmpReg, kryo.getRegistration(reversed.getClass()));
  }

  @Test
  public void enumValueDispatchesToEnumRegistration() {
    Kryo kryo = new SafeKryo();
    Registration enumReg = kryo.register(MyEnum.class);
    // MyEnum.B is an inner class because of the override-style constant; SafeKryo walks up.
    assertSame(enumReg, kryo.getRegistration(MyEnum.B.getClass()));
  }

  @Test
  public void getRegistrationRejectsNull() {
    Kryo kryo = new SafeKryo();
    assertThrows(NullPointerException.class, () -> kryo.getRegistration(null));
  }

  @Test
  public void defaultConstructorUsesTrustedReferenceResolver() throws Exception {
    SafeKryo kryo = new SafeKryo();
    var f = Kryo.class.getDeclaredField("referenceResolver");
    f.setAccessible(true);
    assertTrue(f.get(kryo) instanceof TrustedReferenceResolver);
  }

  @Test
  public void allowsExplicitRegistration() {
    Kryo kryo = new SafeKryo();
    Registration r = kryo.register(StringBuilder.class);
    assertSame(r, kryo.getRegistration(StringBuilder.class));
  }

  @Test
  public void arrayUsedToVerify() {
    // Sanity: ensure Arrays.asList still works to dispatch through List.
    Kryo kryo = new SafeKryo();
    kryo.register(List.class);
    assertNotNull(kryo.getRegistration(Arrays.asList(1, 2).getClass()));
  }

  enum MyEnum {
    A,
    B {
      @Override
      public String label() {
        return "b!";
      }
    };

    public String label() {
      return name();
    }
  }
}
