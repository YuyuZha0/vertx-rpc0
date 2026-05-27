package io.vertxrpc0.transport;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.util.concurrent.FastThreadLocal;
import io.vertxrpc0.invoke.InvokeResult;
import io.vertxrpc0.invoke.InvokeSpec;
import io.vertxrpc0.invoke.ParameterArray;
import io.vertxrpc0.invoke.ResultCode;
import io.vertxrpc0.kryo.KryoFactory;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.concurrent.ArrayBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class KryoMessageTransportTest {

  private final KryoMessageTransport transport = new KryoMessageTransport(new KryoFactory());

  @Test
  public void roundTripInvokeSpec() {
    InvokeSpec spec = new InvokeSpec(7, 123L, "io.example.Svc", "hello",
            MethodType.methodType(String.class, String.class),
            ParameterArray.create(new Object[]{"world"}));

    ByteBuf bytes = transport.serialize(spec);
    InvokeSpec back = (InvokeSpec) transport.deserialize(bytes);
    assertEquals(spec, back);
  }

  @Test
  public void roundTripInvokeResult() {
    InvokeResult result = new InvokeResult(13, 456L, ResultCode.OK, "", "ok-payload");
    ByteBuf bytes = transport.serialize(result);
    InvokeResult back = (InvokeResult) transport.deserialize(bytes);
    assertEquals(result, back);
  }

  @Test
  public void constructorRejectsNullFactory() {
    assertThrows(NullPointerException.class, () -> new KryoMessageTransport(null));
  }

  @Test
  public void factoryReturningNullFailsOnFirstUse() {
    KryoMessageTransport t = new KryoMessageTransport(() -> null);
    InvokeResult result = new InvokeResult(1, 1L, ResultCode.OK, "", 1);
    KryoException ex = assertThrows(KryoException.class, () -> t.serialize(result));
    assertNotNull(ex.getCause());
  }

  @Test
  public void serializeWithCustomAllocatorUsesIt() {
    InvokeResult result = new InvokeResult(1, 2, ResultCode.OK, "", 1);
    ByteBufAllocator allocator = UnpooledByteBufAllocator.DEFAULT;
    ByteBuf buf = transport.serialize(allocator, result);
    try {
      assertSame(allocator, buf.alloc());
    } finally {
      buf.release();
    }
  }

  @Test
  public void deserializeOfEmptyBufferThrowsKryoException() {
    ByteBuf empty = UnpooledByteBufAllocator.DEFAULT.buffer(0);
    assertThrows(KryoException.class, () -> transport.deserialize(empty));
  }

  @Test
  public void kryoIsThreadLocalIsolated() throws Exception {
    KryoMessageTransport t = new KryoMessageTransport(new KryoFactory());
    InvokeResult sample = new InvokeResult(1, 1L, ResultCode.OK, "", 1);
    t.serialize(sample).release();
    Kryo thisThread = readKryoForCurrentThread(t);
    assertNotNull(thisThread);

    ArrayBlockingQueue<Kryo> sink = new ArrayBlockingQueue<>(1);
    Thread other = new Thread(() -> {
      t.serialize(sample).release();
      try {
        sink.put(readKryoForCurrentThread(t));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    other.start();
    other.join();
    assertNotSame(thisThread, sink.poll());
  }

  @SuppressWarnings("unchecked")
  private static Kryo readKryoForCurrentThread(KryoMessageTransport t) {
    try {
      Field field = KryoMessageTransport.class.getDeclaredField("kryoFastThreadLocal");
      field.setAccessible(true);
      FastThreadLocal<Kryo> ftl = (FastThreadLocal<Kryo>) field.get(t);
      return ftl.get();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
