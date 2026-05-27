package io.vertxrpc0.kryo.serializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.esotericsoftware.kryo.Kryo;
import io.vertxrpc0.invoke.InvokeResult;
import io.vertxrpc0.invoke.ResultCode;
import io.vertxrpc0.kryo.KryoFactory;
import io.vertxrpc0.testutil.KryoRoundtrip;
import org.junit.jupiter.api.Test;

public class InvokeResultSerializerTest {

  private final Kryo kryo = new KryoFactory().get();

  @Test
  public void roundTripOkWithStringResult() {
    InvokeResult r = new InvokeResult(1, 100L, ResultCode.OK, "", "ok");
    assertEquals(r, KryoRoundtrip.roundtrip(kryo, r));
  }

  @Test
  public void roundTripError() {
    InvokeResult r = new InvokeResult(99, 200L, ResultCode.INVOCATION_ERROR, "boom!", null);
    InvokeResult back = KryoRoundtrip.roundtrip(kryo, r);
    assertEquals(r, back);
    assertNull(back.getResult());
  }

  @Test
  public void roundTripNumericResult() {
    InvokeResult r = new InvokeResult(7, 300L, ResultCode.OK, "", 42L);
    assertEquals(r, KryoRoundtrip.roundtrip(kryo, r));
  }
}
