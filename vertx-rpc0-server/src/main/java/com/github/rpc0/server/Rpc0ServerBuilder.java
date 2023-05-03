package com.github.rpc0.server;

import com.google.common.base.Preconditions;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.MutableClassToInstanceMap;
import com.github.rpc0.conf.AbstractConfigurator;
import com.github.rpc0.conf.ConstructingProcess;
import com.github.rpc0.kryo.KryoFactory;
import com.github.rpc0.transport.KryoMessageTransport;
import com.github.rpc0.transport.MessageTransport;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServerOptions;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;

/**
 * @author fishzhao
 * @since 2021-12-24
 */
@Slf4j
public final class Rpc0ServerBuilder extends AbstractConfigurator<Rpc0ServerBuilder> implements ConstructingProcess<Rpc0Server> {

  private final ClassToInstanceMap<Object> registry = MutableClassToInstanceMap.create();
  private final Vertx vertx;
  private final NetServerOptions netServerOptions;
  private Duration keepAliveDuration = Duration.ofMinutes(2); // 如果连接五分钟未活动则关闭

  public Rpc0ServerBuilder(@NonNull Vertx vertx,
                           @NonNull NetServerOptions netServerOptions,
                           @NonNull ClassLoader classLoader) {
    super(classLoader);
    this.vertx = vertx;
    this.netServerOptions = netServerOptions;
  }

  public Rpc0ServerBuilder(Vertx vertx, NetServerOptions netServerOptions) {
    this(vertx, netServerOptions, Vertx.class.getClassLoader());
  }

  @Override
  public Rpc0Server build() {
    Preconditions.checkArgument(!registry.isEmpty(), "No service has been registered!");
    Map<String, Object> classNameInstanceMap = Maps.newHashMapWithExpectedSize(registry.size());
    for (Map.Entry<Class<?>, Object> entry : registry.entrySet()) {
      classNameInstanceMap.put(
              entry.getKey().getTypeName(),
              entry.getValue()
      );
    }
    ServiceLookup serviceLookup = new ServiceLookup(ImmutableMap.copyOf(classNameInstanceMap));
    MessageTransport messageTransport = new KryoMessageTransport(new KryoFactory(getClassLoader(), getKryoRegistry()));
    return new Rpc0Server(serviceLookup,
            messageTransport,
            netServerOptions,
            keepAliveDuration);
  }

  public Rpc0ServerBuilder setKeepAliveDuration(@NonNull Duration keepAliveDuration) {
    this.keepAliveDuration = keepAliveDuration;
    return this;
  }

  public <T> Rpc0ServerBuilder addBinding(@NonNull Class<? super T> type, @NonNull T instance) {
    Preconditions.checkArgument(type.isInterface(), "%s is not a interface!", type);
    registry.put(type, instance);
    log.info("Add service binding: {} -> {}", type, instance);
    return this;
  }
}
