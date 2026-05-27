package io.vertxrpc0.server;

import com.google.common.base.Preconditions;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.MutableClassToInstanceMap;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServerOptions;
import io.vertxrpc0.conf.AbstractConfigurator;
import io.vertxrpc0.conf.ConstructingProcess;
import io.vertxrpc0.kryo.KryoFactory;
import io.vertxrpc0.kryo.KryoRegistry;
import io.vertxrpc0.transport.KryoMessageTransport;
import io.vertxrpc0.transport.MessageTransport;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * @author fishzhao
 * @since 2021-12-24
 */
@Slf4j
public final class Rpc0ServerBuilder extends AbstractConfigurator<Rpc0ServerBuilder>
    implements ConstructingProcess<Rpc0Server> {

  private final ClassToInstanceMap<Object> registry = MutableClassToInstanceMap.create();
  private final Vertx vertx;
  private final NetServerOptions netServerOptions;
  private Duration keepAliveDuration = Duration.ofMinutes(2);

  public Rpc0ServerBuilder(
      @NonNull Vertx vertx,
      @NonNull NetServerOptions netServerOptions,
      @NonNull ClassLoader classLoader) {
    super(classLoader);
    this.vertx = vertx;
    this.netServerOptions = netServerOptions;
  }

  public Rpc0ServerBuilder(Vertx vertx, NetServerOptions netServerOptions) {
    this(vertx, netServerOptions, Vertx.class.getClassLoader());
  }

  /** Builds a single {@link Rpc0Server} instance. Equivalent to {@code buildSupplier().get()}. */
  @Override
  public Rpc0Server build() {
    return buildSupplier().get();
  }

  /**
   * Returns a {@link Supplier} that mints a fresh {@link Rpc0Server} per call.
   *
   * <p>Use this when deploying multiple server instances behind one port:
   *
   * <pre>
   * Supplier&lt;Rpc0Server&gt; supplier = builder.buildSupplier();
   * vertx.deployVerticle(supplier::get, new DeploymentOptions().setInstances(4));
   * </pre>
   *
   * <p>The builder's current configuration is snapshotted at the time this method is called —
   * subsequent mutations to the builder (additional {@code addBinding} / {@code
   * setKeepAliveDuration} calls) do not affect the returned supplier. The empty-registry check is
   * performed eagerly here, not at supplier-invocation time, so misconfiguration surfaces at the
   * build call site.
   */
  public Supplier<Rpc0Server> buildSupplier() {
    Preconditions.checkArgument(!registry.isEmpty(), "No service has been registered!");
    Map<String, Object> classNameInstanceMap = Maps.newHashMapWithExpectedSize(registry.size());
    for (Map.Entry<Class<?>, Object> entry : registry.entrySet()) {
      classNameInstanceMap.put(entry.getKey().getTypeName(), entry.getValue());
    }
    ImmutableMap<String, Object> services = ImmutableMap.copyOf(classNameInstanceMap);
    ClassLoader cl = getClassLoader();
    KryoRegistry kryoRegistry = getKryoRegistry();
    NetServerOptions opts = netServerOptions;
    Duration keepAlive = keepAliveDuration;

    return () -> {
      ServiceLookup lookup = new ServiceLookup(services);
      MessageTransport transport = new KryoMessageTransport(new KryoFactory(cl, kryoRegistry));
      return new Rpc0Server(lookup, transport, opts, keepAlive);
    };
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
