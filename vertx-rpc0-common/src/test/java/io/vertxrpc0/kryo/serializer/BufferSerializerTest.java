package io.vertxrpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import io.vertx.core.buffer.Buffer;
import io.vertxrpc0.kryo.KryoFactory;
import io.vertxrpc0.testutil.KryoRoundtrip;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class BufferSerializerTest {

  private final Kryo kryo = new KryoFactory().get();

  @Test
  public void roundTripNull() {
    assertNull(KryoRoundtrip.roundtrip(kryo, (Buffer) null));
  }

  @Test
  public void roundTripEmpty() {
    Buffer empty = Buffer.buffer();
    Buffer back = KryoRoundtrip.roundtrip(kryo, empty);
    assertEquals(0, back.length());
    assertEquals(empty, back);
  }

  @Test
  public void roundTripBytes() {
    byte[] payload = {1, 2, 3, 4, 5, 6, 7};
    Buffer buf = Buffer.buffer(payload);
    Buffer back = KryoRoundtrip.roundtrip(kryo, buf);
    assertEquals(buf, back);
  }

  @Test
  public void roundTripLargeBuffer() {
    byte[] payload = new byte[1024];
    for (int i = 0; i < payload.length; i++) payload[i] = (byte) i;
    Buffer buf = Buffer.buffer(payload);
    assertEquals(buf, KryoRoundtrip.roundtrip(kryo, buf));
  }
}
