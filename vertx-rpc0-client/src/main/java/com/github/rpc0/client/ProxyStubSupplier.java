package com.github.rpc0.client;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.github.rpc0.transport.MessageTransport;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.NetSocketInternal;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * @author fishzhao
 * @since 2022-01-25
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class ProxyStubSupplier implements Supplier<Future<ProxyStub>>, Closeable {


  private static final int MAX_CONNECT_ATTEMPTS = 10;

  private final Timer timer = new HashedWheelTimer(
          new ThreadFactoryBuilder()
                  .setDaemon(true)
                  .setNameFormat("vertx-rpc0-timeout-ticker-%d")
                  .build()
  );
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final AtomicReference<Future<ProxyStub>> futureRef = new AtomicReference<>(null);

  private final ContextInternal context;
  private final NetClient netClient;
  private final MessageTransport messageTransport;
  private final Duration timeout;
  private final String host;
  private final int port;

  private int connectAttempts = 0;

  @Override
  public Future<ProxyStub> get() {
    Future<ProxyStub> cachedFuture;
    if ((cachedFuture = futureRef.get()) != null) {
      return cachedFuture;
    }
    Promise<ProxyStub> promise = context.promise();
    if (isActive()) {
      context.runOnContext(v -> {
        if (connectAttempts > MAX_CONNECT_ATTEMPTS) {
          promise.tryFail("Connecting fail over maximum limit: " + MAX_CONNECT_ATTEMPTS);
          return;
        }
        Future<ProxyStub> future;
        if ((future = futureRef.get()) != null) {
          future.onComplete(promise);
          return;
        }
        //increase the attempts
        ++connectAttempts;
        createProxyStub(promise);
        Future<ProxyStub> currentFuture = promise.future();
        futureRef.set(currentFuture);
        currentFuture.onComplete(result -> {
          if (result.succeeded()) {
            connectAttempts = 0;
          } else if (isActive()) {
            //ensure set itself to null
            futureRef.compareAndSet(currentFuture, null);
          }
        });
      });
    } else {
      promise.fail("Connection unavailable for already closed!");
    }
    return promise.future();
  }

  private void createProxyStub(Promise<ProxyStub> promise) {
    netClient.connect(port, host, result -> {
      if (result.succeeded()) {
        try {
          NetSocket socket = result.result();
          ProxyStub proxyStub = new ProxyStub((NetSocketInternal) socket, messageTransport, timer, timeout);
          proxyStub.registerHandlers(this::onConnectionDispose);
          promise.complete(proxyStub);
          log.info("Open connection to [{}] successfully",
                  socket.remoteAddress());
        } catch (Throwable cause) {
          promise.tryFail(cause);
        }
      } else {
        promise.tryFail(result.cause());
      }
    });
  }

  private void onConnectionDispose() {
    if (isActive()) {
      futureRef.set(null);
    }
  }

  private boolean isActive() {
    return !closed.get();
  }

  @Override
  public void close(Promise<Void> completion) {
    if (closed.compareAndSet(false, true)) {
      Future<ProxyStub> future;
      if ((future = futureRef.get()) != null) {
        future.onComplete(result -> {
          if (result.succeeded()) {
            result.result().close(completion);
          } else {
            completion.complete();
          }
        });
      } else {
        completion.complete();
      }
    } else {
      completion.fail("ProxyStubSupplier already closed!");
    }
  }
}
