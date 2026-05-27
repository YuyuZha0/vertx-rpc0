package io.vertxrpc0.server;

import com.google.common.collect.Sets;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertxrpc0.transport.MessageTransport;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author fishzhao
 * @since 2021-12-20
 */
@Slf4j
public final class Rpc0Server extends AbstractVerticle {

  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final Set<ServiceInvoker> invokers = Sets.newIdentityHashSet();
  private final ServiceLookup serviceLookup;
  private final MessageTransport messageTransport;
  private final NetServerOptions netServerOptions;
  private final long keepAliveMills;
  private NetServer netServer;

  Rpc0Server(@NonNull ServiceLookup serviceLookup,
             @NonNull MessageTransport messageTransport,
             @NonNull NetServerOptions netServerOptions,
             @NonNull Duration keepAliveDuration) {
    this.serviceLookup = serviceLookup;
    this.messageTransport = messageTransport;
    this.netServerOptions = netServerOptions;
    this.keepAliveMills = keepAliveDuration.toMillis();
  }

  @Override
  public void start(Promise<Void> startPromise) {
    NetServer server = vertx.createNetServer(netServerOptions);
    server.connectHandler(this::handleConnect);
    server.listen().onComplete(result -> {
      if (result.failed()) {
        startPromise.tryFail(result.cause());
        return;
      }
      this.netServer = server;
      if (keepAliveMills > 0) {
        // Scan often enough to actually evict connections near the keep-alive
        // boundary: every keepAliveMills/4 (but no faster than once a second).
        long scanInterval = Math.max(1000L, keepAliveMills / 4);
        vertx.setPeriodic(scanInterval, timerId -> {
          if (closed.get()) {
            vertx.cancelTimer(timerId);
            return;
          }
          // scanCloseInactive iterates the non-thread-safe `invokers` set, so it
          // must run on the same context as add/remove. Vert.x currently fires
          // setPeriodic callbacks on the calling context, but the runOnContext
          // is a cheap defensive anchor in case that ever changes.
          context.runOnContext(v -> scanCloseInactive());
        });
      }
      startPromise.complete();
    });
  }

  private void scanCloseInactive() {
    if (invokers.isEmpty()) {
      return;
    }
    long currentTime = System.currentTimeMillis();
    for (ServiceInvoker invoker : invokers) {
      long lastActiveTime = invoker.lastActiveTime();
      if (lastActiveTime > 0 && currentTime - lastActiveTime > keepAliveMills) {
        invoker.getSocket().close()
                .onFailure(cause -> log.debug("Failed to close inactive socket: {}", cause.toString()));
      }
    }
  }

  private void handleConnect(NetSocket netSocket) {
    ServiceInvoker invoker = new ServiceInvoker(
            netSocket,
            messageTransport,
            serviceLookup
    );
    // The add runs synchronously here — connectHandler is dispatched on the
    // verticle's context.
    //
    // The dispose lambda, on the other hand, fires from ServiceInvoker's
    // socket.closeHandler. Under current Vert.x that callback runs on the
    // socket's owning context (== this verticle's context), but we route it
    // through context.runOnContext defensively so future Vert.x changes —
    // or callers wiring registerHandlers from a different context — can't
    // corrupt the non-thread-safe identity set.
    invoker.registerHandlers(() -> context.runOnContext(v -> invokers.remove(invoker)));
    invokers.add(invoker);
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    closed.set(true);
    if (netServer != null) {
      netServer.close().onComplete(stopPromise);
    } else {
      stopPromise.tryComplete();
    }
  }
}
