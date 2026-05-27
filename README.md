# vertx-rpc0

A lightweight, high-performance Java RPC framework built on top of [Eclipse Vert.x](https://vertx.io)
and [Kryo](https://github.com/EsotericSoftware/kryo). It lets you expose plain Java interfaces over the network and call
them remotely as if they were local — with strict, opt-in type registration as a defense against deserialization
attacks.

## Why vertx-rpc0

- **Async by design.** Service methods return `io.vertx.core.Future<T>`, so the entire request lifecycle fits the Vert.x
  event-loop model with no blocking.
- **Fast wire format.** Kryo with custom serializers for collections, maps, immutable Guava types, `MethodType`, ASCII
  strings, and Vert.x `Buffer`.
- **Trusted-types-only.** A hardened `SafeKryo` rejects unregistered classes; user-defined payloads must be opted in
  with `@TrustedType` or a package scan.
- **Small footprint.** Four small modules — `common`, `client`, `server`, `example` — with no transitive runtime
  surprises beyond Vert.x + Kryo + Guava.

## Where this fits

`vertx-rpc0` is intentionally narrow. It does serialization, transport, async
request/response correlation, and connection lifecycle — and not much else.
The features that older Java RPC stacks (e.g. Dubbo) build *into the
application layer* — service discovery, load balancing, retries, mTLS,
observability, circuit breakers — are deliberately omitted, on the
assumption that they're now better handled in one of two places:

- **Service governance → Kubernetes + a service mesh.** Discovery via
  Service DNS, load balancing via `kube-proxy`, mTLS / retries / circuit
  breaking / per-request tracing via the mesh data plane (Istio + Envoy,
  Linkerd, …). None of that needs to live in the framework anymore. The
  "fat framework, thin platform" answer that defined RPC frameworks
  c. 2015 has flipped — the platform is now fat, and the framework should
  be thin.
- **L7 ergonomics and cross-language interop → use the tool built for
  it.** Cross-language RPC: gRPC. Typed HTTP clients in Java: Retrofit or
  OpenFeign. REST in Spring: `RestClient`. `vertx-rpc0` does not try to
  reinvent any of these.

What's left — and what this framework is actually *for* — is one niche:

> **Java-async, hot-path, intra-cluster, trusted-network RPC** where
> you want a Kryo-dense binary wire, full control over the data path,
> and no Envoy sidecar between caller and callee.

That niche is real but narrow. If any of the following are true, reach for
something else:

| Need                                                 | Use                                                                                                                       |
|------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| Cross-language clients (Go, Python, Node…)           | gRPC                                                                                                                      |
| Public API or third-party callers                    | gRPC, or REST + OpenAPI                                                                                                   |
| L7 mesh routing / per-RPC tracing through Istio      | gRPC (HTTP/2 is L7-parseable by Envoy; Kryo-over-TCP is not — `vertx-rpc0` through a sidecar reduces to L4 byte counting) |
| Built-in discovery / LB / retries / circuit breakers | Dubbo, or run `vertx-rpc0` behind a mesh while accepting that mesh-level L7 features won't apply                          |
| HTTP ergonomics in Java                              | Retrofit, OpenFeign, Spring `RestClient`                                                                                  |
| Streaming / server-push / bidirectional              | gRPC                                                                                                                      |

The framework is ~80 source files. You can read it end-to-end in an
afternoon and know exactly what's on the wire and what isn't — that's
the design budget. The "rpc0" in the name reads literally: zero
ceremony, zero discovery, zero L7, zero "what about my multi-language
clients". Embrace the niche or pick a different tool.

## Requirements

- **JDK 21** or later (uses `MethodHandles`, switch expressions, modern reflection).
- **Maven 3.6+** to build.

## Coordinates

```xml
<dependency>
  <groupId>io.vertxrpc0</groupId>
  <artifactId>vertx-rpc0-server</artifactId>
  <version>1.0.0</version>
</dependency>

<dependency>
  <groupId>io.vertxrpc0</groupId>
  <artifactId>vertx-rpc0-client</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Quickstart

### 1. Define a service interface

Every method must return `io.vertx.core.Future<T>`.

```java
package io.vertxrpc0.service;

import io.vertx.core.Future;

public interface HelloService {

  Future<String> sayHello(String name);
}
```

### 2. Implement the interface

```java
package io.vertxrpc0.service.impl;

import io.vertx.core.Future;
import io.vertxrpc0.service.HelloService;

public final class HelloServiceImpl implements HelloService {

  @Override
  public Future<String> sayHello(String name) {
    return Future.succeededFuture("Hello, " + name);
  }
}
```

Method bodies run on the Vert.x event loop — don't block. The framework does not synchronize calls, so any shared
mutable state inside an implementation is your responsibility.

### 3. Start a server

```java
package io.vertxrpc0;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetServerOptions;
import io.vertxrpc0.server.Rpc0Server;
import io.vertxrpc0.server.Rpc0ServerBuilder;
import io.vertxrpc0.service.HelloService;
import io.vertxrpc0.service.impl.HelloServiceImpl;

public final class ExampleServer {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    Rpc0Server server = new Rpc0ServerBuilder(
            vertx,
            new NetServerOptions().setHost(args[0]).setPort(Integer.parseInt(args[1])))
            .addBinding(HelloService.class, new HelloServiceImpl())
            .build();
    vertx.deployVerticle(server);
  }
}
```

### 4. Call from a client

```java
package io.vertxrpc0;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetClientOptions;
import io.vertxrpc0.client.ServiceFactory;
import io.vertxrpc0.client.ServiceFactoryBuilder;
import io.vertxrpc0.service.HelloService;

import java.time.Duration;

public final class ExampleClient {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    ServiceFactory factory = new ServiceFactoryBuilder(
            vertx, "127.0.0.1", 9549,
            new NetClientOptions(),
            Duration.ofSeconds(3),
            Vertx.class.getClassLoader())
            .registerService(HelloService.class)
            .build();

    HelloService helloService = factory.create(HelloService.class);
    helloService.sayHello("world")
            .onSuccess(reply -> System.out.println(reply))
            .onFailure(Throwable::printStackTrace);
  }
}
```

## Multi-Verticle deployment

Both builders expose a `buildSupplier()` overload that returns a
`Supplier<Rpc0Server>` / `Supplier<ServiceFactory>` instead of a single
instance. Pass it to `vertx.deployVerticle(supplier::get,
DeploymentOptions.setInstances(N))` to scale horizontally across event loops.

### Server

N server verticles share one port via Vert.x's built-in port-sharing — Vert.x
binds the underlying listening socket once and dispatches accepted
connections round-robin across the deployed event loops.

```java
Supplier<Rpc0Server> serverSupplier = new Rpc0ServerBuilder(vertx,
        new NetServerOptions().setHost("0.0.0.0").setPort(9549))
        .addBinding(HelloService.class, new HelloServiceImpl())
        .buildSupplier();

vertx.deployVerticle(serverSupplier::get,
        new DeploymentOptions().setInstances(4));
```

### Client

Each client verticle should mint **its own** `ServiceFactory` inside its
`start()` — that way the factory binds to that verticle's `Context` and
opens its own connection. `buildSupplier()` snapshots the builder
configuration immediately; `vertx.getOrCreateContext()` and
`vertx.createNetClient(...)` are deferred until `supplier.get()` is called.

```java
Supplier<ServiceFactory> factorySupplier =
        new ServiceFactoryBuilder(vertx, "rpc.svc.cluster.local", 9549)
                .registerService(HelloService.class)
                .buildSupplier();

class MyClientVerticle extends AbstractVerticle {
  private ServiceFactory factory;

  @Override public void start() {
    factory = factorySupplier.get();   // own context, own NetClient
  }
}

vertx.deployVerticle(() -> new MyClientVerticle(factorySupplier),
        new DeploymentOptions().setInstances(4));
```

`build()` still exists for the single-instance case and captures the
calling thread's context eagerly; reach for `buildSupplier()` when you
need multiple instances.

## Supported parameter and return types

### Value types

All primitives, their boxed wrappers, primitive arrays, and the following references (plus their `T[]` array forms):

`Byte`, `Boolean`, `Character`, `Short`, `Integer`, `Float`, `Double`, `String`, `BitSet`, `URL`, `Charset`, `Currency`,
`BigInteger`, `BigDecimal`, `Date`, `Calendar`, `TimeZone`, `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetDateTime`,
`ZonedDateTime`, `Duration`, `ZoneId`, `Instant`, `io.netty.util.AsciiString`, `io.vertx.core.buffer.Buffer`.

### Collections

Declare parameters and return types using one of these interfaces — the framework picks the right serializer
automatically:

`List`, `Set`, `SortedSet`, `Queue`, `Deque`, `Collection`, plus Guava's `ImmutableCollection` family.

If you need a specific implementation, the following concrete classes are pre-registered: `ArrayList`, `LinkedList`,
`LinkedHashSet`, `HashSet`, `TreeSet`, `ArrayDeque`, `PriorityQueue`. Anything outside this list throws on send.

### Maps

Declared interface: `Map`, `SortedMap`, or Guava's `ImmutableMap`. Pre-registered implementations: `HashMap`,
`LinkedHashMap`, `TreeMap`, `Properties`.

### Custom types

Annotate the class with `@TrustedType(typeId = N)` and either register it explicitly or expose it via a package scan.
The `typeId` must be unique within a registry.

```java
package io.vertxrpc0.model;

import io.vertxrpc0.annotation.TrustedType;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
@TrustedType(typeId = 1)
public class User {
  private long id;
  private String name;
  private OffsetDateTime createTime;
  private List<String> tags;
  private Map<String, Object> attributes;
}
```

Register it on **both** sides — server and client — before `build()`:

```java
// Scan a whole package
builder.registerTypes("io.vertxrpc0.model", false);

// Or register an individual class
builder.registerType(User.class);
```

A custom `KryoRegistry` can be supplied instead of `registerTypes` / `registerType`, but the two approaches are mutually
exclusive on a single builder.

## Custom comparators

`Comparator` instances often appear inside payloads — for example, sorted collections. The framework's
`ComparatorSerializer` recognizes a closed set of comparators by structure (not by class name or reflected fields), so
the wire format is independent of JDK or Guava internals.

Wire-safe set:

- **JDK singletons** — `Comparator.naturalOrder()`, `Comparator.reverseOrder()`.
- **Guava singletons** — `Ordering.natural()`, `Ordering.allEqual()`, `Ordering.arbitrary()`,
  `Ordering.usingToString()`.
- **The chainable `io.vertxrpc0.Comparators<T>` family** — for anything that needs composition (`nullsFirst`,
  `nullsLast`, `reverse`, `compound`). Start a chain from one of the five static factories and chain instance methods:

  ```java
  import io.vertxrpc0.Comparators;

  Comparator<User> a = Comparators.<User>natural().reverse().nullsFirst();
  Comparator<User> b = Comparators.<User>natural().compound(Comparators.usingToString());
  ```

  `Comparators` is a sealed abstract class — only the five leaf factories and the four chain operators are
  wire-representable. The chain methods only accept `Comparators<? super T>`, so every reachable composition is
  statically guaranteed to round-trip.

Anything else — lambdas from `Comparator.comparing*` or `Comparator.thenComparing`, anonymous classes, custom
`Comparator` implementations — is **rejected at serialize time** with a `KryoException`. If you really need an arbitrary
`Serializable` comparator on the wire, construct `ComparatorSerializer(true)` to opt into the JDK-serialization
fallback. **That path is a known deserialization-attack surface** and should only be enabled when the channel itself is
trusted.

## SSL/TLS

Vert.x's native TLS support is used unchanged. Pass standard `NetServerOptions` / `NetClientOptions` with `setSsl(true)`
and the desired `KeyCertOptions` / `TrustOptions`. See the Vert.x
documentation: https://vertx.io/docs/vertx-core/java/#ssl

For local testing against a `SelfSignedCertificate`, set `setHostnameVerificationAlgorithm("")` on the client options to
skip hostname verification.

## Building and testing

```sh
mvn clean verify
```

Runs all unit and integration tests across the four modules and generates per-module JaCoCo coverage reports at:

```
vertx-rpc0-{common,client,server,example}/target/site/jacoco/index.html
```

To launch the bundled example end-to-end:

```sh
mvn -pl vertx-rpc0-example -am package
java -jar vertx-rpc0-example/target/vertx-rpc0-example-1.0.0.jar 127.0.0.1 9549
```

## Module layout

| Module               | What's inside                                                                                      |
|----------------------|----------------------------------------------------------------------------------------------------|
| `vertx-rpc0-common`  | Wire transport, Kryo factory + safe registry, custom serializers, configurator base class          |
| `vertx-rpc0-client`  | `ServiceFactory`, dynamic-proxy invocation handler, connection-pooled `ProxyStub`                  |
| `vertx-rpc0-server`  | `Rpc0Server` verticle, per-connection `ServiceInvoker`, `ServiceLookup`                            |
| `vertx-rpc0-example` | Sample services, an `ExampleServer` / `ExampleClient` runnable pair, and the end-to-end test suite |

## License

MIT (see source headers for any third-party portions).
