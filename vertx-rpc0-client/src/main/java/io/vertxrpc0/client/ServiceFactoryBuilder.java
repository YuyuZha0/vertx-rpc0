package io.vertxrpc0.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.NetClientOptions;
import io.vertxrpc0.conf.AbstractConfigurator;
import io.vertxrpc0.conf.ConstructingProcess;
import io.vertxrpc0.kryo.KryoFactory;
import io.vertxrpc0.kryo.KryoRegistry;
import io.vertxrpc0.transport.KryoMessageTransport;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * @author fishzhao
 * @since 2021-12-24
 */
@Slf4j
public final class ServiceFactoryBuilder extends AbstractConfigurator<ServiceFactoryBuilder>
    implements ConstructingProcess<ServiceFactory> {

  private final Set<Class<?>> serviceRegistry = new HashSet<>();
  private final Vertx vertx;
  private final NetClientOptions netClientOptions;
  private final Duration timeout;
  private final String host;
  private final int port;

  public ServiceFactoryBuilder(
      @NonNull Vertx vertx,
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
    this(
        vertx,
        host,
        port,
        new NetClientOptions(),
        Duration.ofSeconds(5),
        defaultClassLoader(Vertx.class.getClassLoader()));
  }

  public ServiceFactoryBuilder registerService(@NonNull Class<?> serviceType) {
    Preconditions.checkArgument(serviceType.isInterface(), "%s is not a interface!");
    if (serviceRegistry.add(serviceType)) {
      log.info("Add type \"{}\" to the service registry", serviceType);
    }
    return this;
  }

  /**
   * Builds a single {@link ServiceFactory}. The Vert.x context is captured <strong>at the time this
   * method is called</strong>, so calling {@code build()} from outside a Verticle (e.g. main / test
   * thread) binds the factory to the surrounding root context. For multi-Verticle deployments where
   * each Verticle needs its own context and {@link io.vertx.core.net.NetClient}, use {@link
   * #buildSupplier()} instead and invoke the supplier inside each Verticle's {@code start()}.
   */
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
            port,
            getMaxMsgLen()));
  }

  /**
   * Returns a {@link Supplier} that mints a fresh {@link ServiceFactory} per call.
   *
   * <p>Use this for multi-Verticle deployments: each Verticle invokes the supplier inside its own
   * {@code start()} so the resulting factory captures <em>that Verticle's</em> context and creates
   * <em>its own</em> {@link io.vertx.core.net.NetClient} / connection:
   *
   * <pre>
   * Supplier&lt;ServiceFactory&gt; sup = builder.buildSupplier();
   * vertx.deployVerticle(() -&gt; new MyClientVerticle(sup),
   *         new DeploymentOptions().setInstances(4));
   * </pre>
   *
   * <p>The builder's configuration is snapshotted at the time this method is called; subsequent
   * mutations to the builder don't leak into the supplier.
   */
  @Override
  public Supplier<ServiceFactory> buildSupplier() {
    ImmutableSet<Class<?>> services = ImmutableSet.copyOf(serviceRegistry);
    ClassLoader cl = getClassLoader();
    KryoRegistry kryoRegistry = getKryoRegistry();
    NetClientOptions opts = netClientOptions;
    String h = host;
    int p = port;
    Duration t = timeout;
    int maxLen = getMaxMsgLen();
    Vertx vx = vertx;
    return () ->
        new ServiceFactory(
            services,
            vx,
            new ProxyStubSupplier(
                (ContextInternal) vx.getOrCreateContext(),
                vx.createNetClient(opts),
                new KryoMessageTransport(new KryoFactory(cl, kryoRegistry)),
                t,
                h,
                p,
                maxLen));
  }
}
