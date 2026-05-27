package io.vertxrpc0.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.impl.NetSocketInternal;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Documents how Vert.x's {@link io.vertx.core.net.NetSocket#write(Buffer)} handles the ref-count of
 * an externally-allocated Netty {@link ByteBuf} wrapped in a {@link Buffer}. Observed behavior
 * (verified by these assertions): Vert.x does <strong>not</strong> release the ByteBuf when the
 * write completes — for any allocator, including the socket's own channel allocator. Callers
 * therefore must release the ByteBuf themselves in the write callback (which is what {@code
 * ProxyStub.call} and {@code ServiceInvoker.writeResult} do). If Vert.x changes its behavior in a
 * future release, these assertions will fail loudly.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class NetSocketByteBufOwnershipTest {

  @Test
  public void writeDoesNotReleaseChannelAllocatorByteBuf(Vertx vertx, VertxTestContext ctx) {
    setUpAndExchange(vertx, ctx, /* useChannelAllocator */ true);
  }

  @Test
  public void writeDoesNotReleaseExternalUnpooledByteBuf(Vertx vertx, VertxTestContext ctx) {
    setUpAndExchange(vertx, ctx, /* useChannelAllocator */ false);
  }

  /**
   * Boots a server, connects a client, allocates a fresh ByteBuf, writes it through the client
   * socket, and asserts the ref-count is still 1 by the time {@code write().onComplete} fires —
   * i.e. Vert.x did not release it.
   */
  private void setUpAndExchange(Vertx vertx, VertxTestContext ctx, boolean useChannelAllocator) {
    NetServer server =
        vertx.createNetServer(new NetServerOptions().setHost("127.0.0.1").setPort(0));
    AtomicInteger bytesReceived = new AtomicInteger();
    server.connectHandler(sock -> sock.handler(buf -> bytesReceived.addAndGet(buf.length())));

    server
        .listen()
        .onComplete(
            ctx.succeeding(
                listening -> {
                  NetClient client = vertx.createNetClient();
                  client
                      .connect(listening.actualPort(), "127.0.0.1")
                      .onComplete(
                          ctx.succeeding(
                              socket -> {
                                NetSocketInternal internal = (NetSocketInternal) socket;
                                ByteBuf buf =
                                    useChannelAllocator
                                        ? internal.channelHandlerContext().alloc().buffer(8)
                                        : UnpooledByteBufAllocator.DEFAULT.buffer(8);
                                buf.writeBytes(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
                                assertEquals(
                                    1, buf.refCnt(), "fresh allocation must have refCnt=1");

                                socket
                                    .write(Buffer.buffer(buf))
                                    .onComplete(
                                        ctx.succeeding(
                                            v ->
                                                ctx.verify(
                                                    () -> {
                                                      // Vert.x does not take ownership of
                                                      // externally-allocated ByteBufs — callers
                                                      // remain responsible for releasing. Asserting
                                                      // refCnt=1 documents this so a
                                                      // future Vert.x change to auto-release would
                                                      // surface here.
                                                      assertEquals(
                                                          1,
                                                          buf.refCnt(),
                                                          "expected Vert.x to leave refCnt=1 (caller-owned); actual refCnt="
                                                              + buf.refCnt()
                                                              + " (alloc="
                                                              + (useChannelAllocator
                                                                  ? "channel"
                                                                  : "unpooled-external")
                                                              + ")");
                                                      buf.release(); // we own it; clean up after
                                                      // ourselves.
                                                      socket.close();
                                                      server.close();
                                                      ctx.completeNow();
                                                    })));
                              }));
                }));
  }
}
