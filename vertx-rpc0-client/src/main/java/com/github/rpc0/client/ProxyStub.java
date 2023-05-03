package com.github.rpc0.client;

import com.github.rpc0.invoke.InvokeResult;
import com.github.rpc0.invoke.InvokeSpec;
import com.github.rpc0.transport.MarkedLenMessageHandler;
import com.github.rpc0.transport.MessageTransport;
import com.github.rpc0.transport.ParserHandler;
import com.github.rpc0.transport.Prefix;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.Timer;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.impl.NetSocketInternal;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author fishzhao
 * @since 2021-12-16
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class ProxyStub implements ParserHandler, Closeable {

  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final ConcurrentMap<Long, Promise<InvokeResult>> resultMap = new ConcurrentHashMap<>();
  private final NetSocketInternal socket;
  private final MessageTransport messageTransport;
  private final Timer timer;
  private final Duration timeout;

  boolean isClosed() {
    return closed.get();
  }

  void registerHandlers(Runnable dispose) {
    socket.handler(new MarkedLenMessageHandler(this));
    socket.closeHandler(v -> {
      cleanup(new VertxException("Connection closed!", true));
      if (dispose != null) {
        try {
          dispose.run();
        } catch (Exception ignore) {
        }
      }
    });
  }

  Future<InvokeResult> call(InvokeSpec invokeSpec) {
    Promise<InvokeResult> promise = Promise.promise();
    if (isClosed()) {
      promise.fail("Connection already closed!");
      return promise.future();
    }
    ByteBuf request;
    try {
      request = Prefix.prependTo(messageTransport
              .serialize(socket.channelHandlerContext().alloc(), invokeSpec));
    } catch (Exception e) {
      promise.fail(e);
      return promise.future();
    }
    long requestId = invokeSpec.getRequestId();
    Promise<InvokeResult> old = resultMap.put(requestId, promise);
    if (old != null) {
      old.tryFail("Duplicated requestId: " + request);
    }
    socket.write(Buffer.buffer(request), result -> {
      if (result.succeeded()) {
        registerTimeout(requestId);
      } else {
        resultMap.remove(requestId);
        promise.tryFail(result.cause());
      }
    });
    return promise.future();
  }

  private void registerTimeout(long requestId) {
    if (resultMap.containsKey(requestId)) {
      timer.newTimeout(timeout -> {
        Promise<InvokeResult> promise = resultMap.remove(requestId);
        if (promise != null) {
          promise.tryFail("Timeout");
        }
      }, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
  }

  @Override
  public void fetal(Throwable cause) {
    cleanup(cause);
  }

  @Override
  public void handle(Buffer buffer) {
    ByteBuf byteBuf = buffer.getByteBuf();
    try {
      InvokeResult result = (InvokeResult) messageTransport.deserialize(byteBuf);
      Promise<InvokeResult> promise = resultMap.remove(result.getRequestId());
      if (promise != null) {
        promise.tryComplete(result);
      }
    } finally {
      ReferenceCountUtil.release(byteBuf);
    }
  }

  @Override
  public void close(Promise<Void> completion) {
    if (closed.compareAndSet(false, true)) {
      socket.close(completion);
    } else {
      completion.fail("ProxyStub already closed!");
    }
  }

  private void cleanup(Throwable cause) {
    List<Promise<?>> promises = new ArrayList<>(resultMap.values());
    resultMap.clear();
    for (Promise<?> promise : promises) {
      promise.tryFail(cause);
    }
  }
}
