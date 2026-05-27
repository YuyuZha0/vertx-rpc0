package io.vertxrpc0.client;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertxrpc0.transport.MessageTransport;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Caches a single {@link ProxyStub} per supplier instance, reconnecting on demand with
 * exponential backoff. The cached future is shared across concurrent callers so failed
 * connect attempts collapse into one round-trip and don't trigger a connect storm.
 *
 * @author fishzhao
 * @since 2022-01-25
 */
@Slf4j
final class ProxyStubSupplier implements Supplier<Future<ProxyStub>>, Closeable {

  private static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofMillis(100);
  private static final Duration DEFAULT_MAX_BACKOFF = Duration.ofSeconds(30);

  // Wheel timer is used by ProxyStub for per-RPC call timeouts (off the event loop).
  // Reconnect scheduling instead uses ContextInternal#setTimer so the clear runs on the
  // same event loop where futureRef is mutated.
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
  private final long initialBackoffMillis;
  private final long maxBackoffMillis;

  // Touched only from the supplier's Vert.x context — no atomicity needed.
  private int consecutiveFailures = 0;

  ProxyStubSupplier(@NonNull ContextInternal context,
                    @NonNull NetClient netClient,
                    @NonNull MessageTransport messageTransport,
                    @NonNull Duration timeout,
                    @NonNull String host,
                    int port) {
    this(context, netClient, messageTransport, timeout, host, port,
            DEFAULT_INITIAL_BACKOFF, DEFAULT_MAX_BACKOFF);
  }

  ProxyStubSupplier(@NonNull ContextInternal context,
                    @NonNull NetClient netClient,
                    @NonNull MessageTransport messageTransport,
                    @NonNull Duration timeout,
                    @NonNull String host,
                    int port,
                    @NonNull Duration initialBackoff,
                    @NonNull Duration maxBackoff) {
    if (initialBackoff.isZero() || initialBackoff.isNegative()) {
      throw new IllegalArgumentException("initialBackoff must be positive: " + initialBackoff);
    }
    if (maxBackoff.compareTo(initialBackoff) < 0) {
      throw new IllegalArgumentException(
              "maxBackoff (" + maxBackoff + ") must be >= initialBackoff (" + initialBackoff + ")");
    }
    this.context = context;
    this.netClient = netClient;
    this.messageTransport = messageTransport;
    this.timeout = timeout;
    this.host = host;
    this.port = port;
    this.initialBackoffMillis = initialBackoff.toMillis();
    this.maxBackoffMillis = maxBackoff.toMillis();
  }

  @Override
  public Future<ProxyStub> get() {
    Future<ProxyStub> cached = futureRef.get();
    if (cached != null) {
      return cached;
    }
    if (!isActive()) {
      return Future.failedFuture("Connection unavailable for already closed!");
    }
    Promise<ProxyStub> promise = context.promise();
    context.runOnContext(v -> tryConnect(promise));
    return promise.future();
  }

  private void tryConnect(Promise<ProxyStub> promise) {
    if (!isActive()) {
      promise.tryFail("Connection unavailable for already closed!");
      return;
    }
    Future<ProxyStub> cached = futureRef.get();
    if (cached != null) {
      cached.onComplete(promise);
      return;
    }
    Future<ProxyStub> newFuture = promise.future();
    futureRef.set(newFuture);

    netClient.connect(port, host).onComplete(result -> {
      if (result.succeeded()) {
        NetSocket socket = result.result();
        try {
          ProxyStub stub = new ProxyStub(socket, messageTransport, timer, timeout);
          stub.registerHandlers(() -> onConnectionDispose(newFuture));
          consecutiveFailures = 0;
          log.info("Open connection to [{}] successfully", socket.remoteAddress());
          promise.complete(stub);
        } catch (Throwable cause) {
          socket.close();
          handleConnectFailure(promise, newFuture, cause);
        }
      } else {
        handleConnectFailure(promise, newFuture, result.cause());
      }
    });
  }

  private void handleConnectFailure(Promise<ProxyStub> promise,
                                    Future<ProxyStub> newFuture,
                                    Throwable cause) {
    consecutiveFailures++;
    long backoff = computeBackoffMillis(consecutiveFailures);
    log.warn("Connect to [{}:{}] failed (attempt #{}); next attempt in {}ms: {}",
            host, port, consecutiveFailures, backoff,
            cause == null ? "<no cause>" : cause.toString());
    // Hold the failed future in cache for the backoff window: concurrent callers fail
    // fast on the cached failure instead of opening parallel connects. The clear runs on
    // the supplier's event loop, same as every other futureRef mutation.
    context.setTimer(backoff, id -> {
      if (isActive()) {
        futureRef.compareAndSet(newFuture, null);
      }
    });
    promise.tryFail(cause);
  }

  private long computeBackoffMillis(int attempts) {
    long backoff = initialBackoffMillis;
    // Exponential, capped at maxBackoffMillis. Saturating shift to avoid overflow.
    for (int i = 1; i < attempts && backoff < maxBackoffMillis; i++) {
      backoff = Math.min(maxBackoffMillis, backoff << 1);
      if (backoff <= 0) {
        return maxBackoffMillis;
      }
    }
    return Math.min(backoff, maxBackoffMillis);
  }

  private void onConnectionDispose(Future<ProxyStub> ownerFuture) {
    // CAS so a late dispose for an old connection doesn't clobber a newer one.
    if (isActive()) {
      futureRef.compareAndSet(ownerFuture, null);
    }
  }

  private boolean isActive() {
    return !closed.get();
  }

  @Override
  public void close(Promise<Void> completion) {
    if (!closed.compareAndSet(false, true)) {
      completion.fail("ProxyStubSupplier already closed!");
      return;
    }
    Promise<Void> drain = Promise.promise();
    drain.future().onComplete(ar -> {
      try {
        timer.stop();
      } catch (Exception ignored) {
      }
      completion.handle(ar);
    });
    Future<ProxyStub> future = futureRef.getAndSet(null);
    if (future == null) {
      drain.complete();
      return;
    }
    future.onComplete(result -> {
      if (result.succeeded()) {
        result.result().close(drain);
      } else {
        // Failed future means there's no stub to close; just unwind.
        drain.complete();
      }
    });
  }
}
