package com.github.rpc0.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.github.rpc0.conf.AbstractConfigurator;
import com.github.rpc0.conf.ConstructingProcess;
import com.github.rpc0.kryo.KryoFactory;
import com.github.rpc0.transport.KryoMessageTransport;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.NetClientOptions;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * @author fishzhao
 * @since 2021-12-24
 */
@Slf4j
public final class ServiceFactoryBuilder extends AbstractConfigurator<ServiceFactoryBuilder> implements ConstructingProcess<ServiceFactory> {

  private final Set<Class<?>> serviceRegistry = new HashSet<>();
  private final Vertx vertx;
  private final NetClientOptions netClientOptions;
  private final Duration timeout;
  private final String host;
  private final int port;

  public ServiceFactoryBuilder(@NonNull Vertx vertx,
                               @NonNull String host,
                               int port,
                               @NonNull NetClientOptions netClientOptions,
                               @NonNull Duration timeout,
                               @NonNull ClassLoader classLoader) {
    super(classLoader);
    this.vertx = vertx;
    this.netClientOptions = netClientOptions;
    this.timeout = timeout;
    this.host = host;
    this.port = port;
  }

  public ServiceFactoryBuilder(Vertx vertx, String host, int port) {
    this(vertx, host, port, new NetClientOptions(), Duration.ofSeconds(5), Vertx.class.getClassLoader());
  }

  public ServiceFactoryBuilder registerService(@NonNull Class<?> serviceType) {
    Preconditions.checkArgument(
            serviceType.isInterface(),
            "%s is not a interface!"
    );
    if (serviceRegistry.add(serviceType)) {
      log.info("Add type \"{}\" to the service registry", serviceType);
    }
    return this;
  }

  @Override
  public ServiceFactory build() {
    return new ServiceFactory(
            ImmutableSet.copyOf(serviceRegistry),
            vertx,
            new ProxyStubSupplier(
                    (ContextInternal) vertx.getOrCreateContext(),
                    vertx.createNetClient(netClientOptions),
                    new KryoMessageTransport(new KryoFactory(getClassLoader(), getKryoRegistry())),
                    timeout,
                    host,
                    port
            )
    );
  }
}
