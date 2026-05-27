package io.vertxrpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import io.vertxrpc0.kryo.KryoFactory;
import io.vertxrpc0.testutil.KryoRoundtrip;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PropertiesSerializerTest {

  private final Kryo kryo = new KryoFactory().get();

  @Test
  public void roundTripNull() {
    assertNull(KryoRoundtrip.roundtrip(kryo, (Properties) null));
  }

  @Test
  public void roundTripEmpty() {
    Properties p = new Properties();
    Properties back = KryoRoundtrip.roundtrip(kryo, p);
    assertEquals(0, back.size());
  }

  @Test
  public void roundTripWithEntries() {
    Properties p = new Properties();
    p.setProperty("alpha", "1");
    p.setProperty("beta", "two");
    p.setProperty("gamma", "three with spaces");
    assertEquals(p, KryoRoundtrip.roundtrip(kryo, p));
  }

  @Test
  public void copyReturnsEqualClone() {
    Properties p = new Properties();
    p.setProperty("k", "v");
    Properties copy = (Properties) kryo.copy(p);
    assertEquals(p, copy);
    assertEquals("v", copy.getProperty("k"));
  }
}
