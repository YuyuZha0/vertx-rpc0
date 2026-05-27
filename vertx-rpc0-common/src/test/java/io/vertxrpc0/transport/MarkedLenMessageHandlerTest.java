package io.vertxrpc0.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MarkedLenMessageHandlerTest {

  private RecordingHandler handler;

  private static Buffer framed(byte[] payload) {
    ByteBuf payloadBuf = Unpooled.wrappedBuffer(payload);
    ByteBuf prefixed = Prefix.prependTo(payloadBuf);
    try {
      byte[] copy = new byte[prefixed.readableBytes()];
      prefixed.readBytes(copy);
      return Buffer.buffer(copy);
    } finally {
      prefixed.release();
    }
  }

  @BeforeEach
  public void setUp() {
    handler = new RecordingHandler();
  }

  @Test
  public void handlesSingleMessage() {
    MarkedLenMessageHandler h = new MarkedLenMessageHandler(handler);
    byte[] payload = "hello".getBytes();
    h.handle(framed(payload));

    assertEquals(1, handler.bodies.size());
    assertEquals("hello", handler.bodies.get(0).toString());
    assertTrue(handler.failures.isEmpty());
  }

  @Test
  public void handlesSplitBufferReassembly() {
    MarkedLenMessageHandler h = new MarkedLenMessageHandler(handler);
    Buffer full = framed("split".getBytes());
    // Feed one byte at a time.
    for (int i = 0; i < full.length(); i++) {
      h.handle(full.getBuffer(i, i + 1));
    }
    assertEquals(1, handler.bodies.size());
    assertEquals("split", handler.bodies.get(0).toString());
  }

  @Test
  public void handlesBackToBackMessages() {
    MarkedLenMessageHandler h = new MarkedLenMessageHandler(handler);
    Buffer combined =
        Buffer.buffer()
            .appendBuffer(framed("one".getBytes()))
            .appendBuffer(framed("two".getBytes()));
    h.handle(combined);
    assertEquals(2, handler.bodies.size());
    assertEquals("one", handler.bodies.get(0).toString());
    assertEquals("two", handler.bodies.get(1).toString());
  }

  @Test
  public void rejectsBadMagic() {
    MarkedLenMessageHandler h = new MarkedLenMessageHandler(handler);
    Buffer bad = Buffer.buffer(new byte[] {0, 0, 0, 0, 0, 4, 'd', 'a', 't', 'a'});
    h.handle(bad);
    assertEquals(1, handler.failures.size());
    assertTrue(
        handler.failures.get(0).getMessage().contains("Unknown protocol magic"),
        () -> "expected magic-mismatch message, got: " + handler.failures.get(0).getMessage());
  }

  @Test
  public void rejectsNegativeMsgLen() {
    MarkedLenMessageHandler h = new MarkedLenMessageHandler(handler);
    Buffer prefixed = framed(new byte[0]);
    // Overwrite the 4-byte length field with -1.
    Buffer mutated = Buffer.buffer().appendBuffer(prefixed.getBuffer(0, 2)).appendInt(-1);
    h.handle(mutated);
    assertEquals(1, handler.failures.size());
    assertTrue(handler.failures.get(0).getMessage().contains("Invalid msgLen"));
  }

  @Test
  public void rejectsOversizeMsgLen() {
    MarkedLenMessageHandler h = new MarkedLenMessageHandler(handler, 16);
    Buffer prefixed = framed(new byte[0]);
    Buffer mutated = Buffer.buffer().appendBuffer(prefixed.getBuffer(0, 2)).appendInt(17);
    h.handle(mutated);
    assertEquals(1, handler.failures.size());
    assertTrue(handler.failures.get(0).getMessage().contains("Invalid msgLen"));
  }

  @Test
  public void constructorRejectsNonPositiveMaxLen() {
    assertThrows(IllegalArgumentException.class, () -> new MarkedLenMessageHandler(handler, 0));
    assertThrows(IllegalArgumentException.class, () -> new MarkedLenMessageHandler(handler, -1));
  }

  @Test
  public void constructorRejectsNullHandler() {
    assertThrows(NullPointerException.class, () -> new MarkedLenMessageHandler(null));
  }

  private static final class RecordingHandler implements ParserHandler {
    final List<Buffer> bodies = new ArrayList<>();
    final List<Throwable> failures = new ArrayList<>();

    @Override
    public void handle(Buffer event) {
      bodies.add(event);
    }

    @Override
    public void fatal(Throwable cause) {
      assertNotNull(cause);
      failures.add(cause);
    }
  }
}
