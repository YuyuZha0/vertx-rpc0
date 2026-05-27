# vertx-rpc0

A lightweight, high-performance Java RPC framework built on top of [Eclipse Vert.x](https://vertx.io) and [Kryo](https://github.com/EsotericSoftware/kryo). It lets you expose plain Java interfaces over the network and call them remotely as if they were local — with strict, opt-in type registration as a defense against deserialization attacks.

## Why vertx-rpc0

- **Async by design.** Service methods return `io.vertx.core.Future<T>`, so the entire request lifecycle fits the Vert.x event-loop model with no blocking.
- **Fast wire format.** Kryo with custom serializers for collections, maps, immutable Guava types, `MethodType`, ASCII strings, and Vert.x `Buffer`.
- **Trusted-types-only.** A hardened `SafeKryo` rejects unregistered classes; user-defined payloads must be opted in with `@TrustedType` or a package scan.
- **Small footprint.** Four small modules — `common`, `client`, `server`, `example` — with no transitive runtime surprises beyond Vert.x + Kryo + Guava.

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

Method bodies run on the Vert.x event loop — don't block. The framework does not synchronize calls, so any shared mutable state inside an implementation is your responsibility.

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

## Supported parameter and return types

### Value types

All primitives, their boxed wrappers, primitive arrays, and the following references (plus their `T[]` array forms):

`Byte`, `Boolean`, `Character`, `Short`, `Integer`, `Float`, `Double`, `String`, `BitSet`, `URL`, `Charset`, `Currency`, `BigInteger`, `BigDecimal`, `Date`, `Calendar`, `TimeZone`, `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetDateTime`, `ZonedDateTime`, `Duration`, `ZoneId`, `Instant`, `io.netty.util.AsciiString`, `io.vertx.core.buffer.Buffer`.

### Collections

Declare parameters and return types using one of these interfaces — the framework picks the right serializer automatically:

`List`, `Set`, `SortedSet`, `Queue`, `Deque`, `Collection`, plus Guava's `ImmutableCollection` family.

If you need a specific implementation, the following concrete classes are pre-registered: `ArrayList`, `LinkedList`, `LinkedHashSet`, `HashSet`, `TreeSet`, `ArrayDeque`, `PriorityQueue`. Anything outside this list throws on send.

### Maps

Declared interface: `Map`, `SortedMap`, or Guava's `ImmutableMap`. Pre-registered implementations: `HashMap`, `LinkedHashMap`, `TreeMap`, `Properties`.

### Custom types

Annotate the class with `@TrustedType(typeId = N)` and either register it explicitly or expose it via a package scan. The `typeId` must be unique within a registry.

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

A custom `KryoRegistry` can be supplied instead of `registerTypes` / `registerType`, but the two approaches are mutually exclusive on a single builder.

## Custom comparators

`Comparator` instances often appear inside payloads — for example, sorted collections. The framework's `ComparatorSerializer` recognizes a closed set of comparators by structure (not by class name or reflected fields), so the wire format is independent of JDK or Guava internals.

Wire-safe set:

- **JDK singletons** — `Comparator.naturalOrder()`, `Comparator.reverseOrder()`.
- **Guava singletons** — `Ordering.natural()`, `Ordering.allEqual()`, `Ordering.arbitrary()`, `Ordering.usingToString()`.
- **The chainable `io.vertxrpc0.Comparators<T>` family** — for anything that needs composition (`nullsFirst`, `nullsLast`, `reverse`, `compound`). Start a chain from one of the five static factories and chain instance methods:

  ```java
  import io.vertxrpc0.Comparators;

  Comparator<User> a = Comparators.<User>natural().reverse().nullsFirst();
  Comparator<User> b = Comparators.<User>natural().compound(Comparators.usingToString());
  ```

  `Comparators` is a sealed abstract class — only the five leaf factories and the four chain operators are wire-representable. The chain methods only accept `Comparators<? super T>`, so every reachable composition is statically guaranteed to round-trip.

Anything else — lambdas from `Comparator.comparing*` or `Comparator.thenComparing`, anonymous classes, custom `Comparator` implementations — is **rejected at serialize time** with a `KryoException`. If you really need an arbitrary `Serializable` comparator on the wire, construct `ComparatorSerializer(true)` to opt into the JDK-serialization fallback. **That path is a known deserialization-attack surface** and should only be enabled when the channel itself is trusted.

## SSL/TLS

Vert.x's native TLS support is used unchanged. Pass standard `NetServerOptions` / `NetClientOptions` with `setSsl(true)` and the desired `KeyCertOptions` / `TrustOptions`. See the Vert.x documentation: https://vertx.io/docs/vertx-core/java/#ssl

For local testing against a `SelfSignedCertificate`, set `setHostnameVerificationAlgorithm("")` on the client options to skip hostname verification.

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

| Module | What's inside |
|---|---|
| `vertx-rpc0-common` | Wire transport, Kryo factory + safe registry, custom serializers, configurator base class |
| `vertx-rpc0-client` | `ServiceFactory`, dynamic-proxy invocation handler, connection-pooled `ProxyStub` |
| `vertx-rpc0-server` | `Rpc0Server` verticle, per-connection `ServiceInvoker`, `ServiceLookup` |
| `vertx-rpc0-example` | Sample services, an `ExampleServer` / `ExampleClient` runnable pair, and the end-to-end test suite |

## License

MIT (see source headers for any third-party portions).
