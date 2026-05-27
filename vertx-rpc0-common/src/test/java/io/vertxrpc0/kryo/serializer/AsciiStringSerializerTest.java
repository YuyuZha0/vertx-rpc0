package io.vertxrpc0.kryo.serializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.esotericsoftware.kryo.Kryo;
import io.netty.util.AsciiString;
import io.vertxrpc0.kryo.KryoFactory;
import io.vertxrpc0.testutil.KryoRoundtrip;
import org.junit.jupiter.api.Test;

public class AsciiStringSerializerTest {

  private final Kryo kryo = new KryoFactory().get();

  @Test
  public void roundTripNull() {
    assertNull(KryoRoundtrip.roundtrip(kryo, (AsciiString) null));
  }

  @Test
  public void roundTripEmpty() {
    AsciiString empty = AsciiString.of("");
    assertEquals(empty, KryoRoundtrip.roundtrip(kryo, empty));
  }

  @Test
  public void roundTripAsciiContent() {
    AsciiString s = AsciiString.of("hello world");
    AsciiString back = KryoRoundtrip.roundtrip(kryo, s);
    assertEquals(s, back);
    assertEquals("hello world", back.toString());
  }

  @Test
  public void roundTripLargeContent() {
    AsciiString s = AsciiString.of("a".repeat(1024));
    assertEquals(s, KryoRoundtrip.roundtrip(kryo, s));
  }
}
