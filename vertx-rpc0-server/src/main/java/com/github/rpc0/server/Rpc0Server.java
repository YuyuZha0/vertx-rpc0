package com.github.rpc0.server;

import com.google.common.collect.Sets;
import com.github.rpc0.transport.MessageTransport;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.NetSocketInternal;
import lombok.NonNull;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author fishzhao
 * @since 2021-12-20
 */
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
    NetServer netServer = vertx.createNetServer(netServerOptions);
    netServer.connectHandler(this::handleConnect);
    netServer.listen(result -> {
      if (result.succeeded()) {
        synchronized (this) {
          this.netServer = netServer;
        }
        if (keepAliveMills > 0) {
          // 每隔五秒扫描没有发送事件的连接并关闭
          vertx.setPeriodic(5000, timerId -> {
            if (closed.get()) {
              vertx.cancelTimer(timerId);
              return;
            }
            context.runOnContext(v -> scanCloseInactive());
          });
        }
        startPromise.complete();
      } else {
        startPromise.tryFail(result.cause());
      }
    });
  }

  private void scanCloseInactive() {
    if (invokers.isEmpty()) {
      return;
    }
    long currentTime = System.currentTimeMillis();
    for (ServiceInvoker invoker : invokers) {
      long lastActiveTime = invoker.lastActiveTime();
      if (lastActiveTime > 0
          && currentTime - lastActiveTime > keepAliveMills) {
        NetSocketInternal socket = invoker.getSocket();
        socket.close();
      }
    }
  }


  private void handleConnect(NetSocket netSocket) {
    ServiceInvoker invoker = new ServiceInvoker(
            (NetSocketInternal) netSocket,
            messageTransport,
            serviceLookup
    );
    invoker.registerHandlers(() ->
            context.runOnContext(v ->
                    invokers.remove(invoker)));
    context.runOnContext(v -> invokers.add(invoker));
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    closed.set(true);
    synchronized (this) {
      if (netServer != null) {
        netServer.close(stopPromise);
        return;
      }
    }
    stopPromise.tryComplete();
  }
}
