## [零]  A better java rpc framework solution

`vertx-rpc0`是一个轻量级的高性能rpc框架解决方案，底层基于vertx+kryo，可以用来方便地构建高性能网络应用

### Quickstart

#### 第一步 设计一个接口

比如此处，我们设计一个HelloService

```java
package com.github.rpc0.service;

import io.vertx.core.Future;

/**
 * @author fishzhao
 * @since 2022-01-25
 */
public interface HelloService {

  Future<String> sayHello(String name);
}

```

*注意， 所有接口方法的返回类型必须是`io.vertx.core.Future`*
对于方法参数和返回值， rpc内部提供了绝大多数Java常用类型的支持，但对于自定义类型，仍然需要手动进行注册，详情可以参考文档后面的部分。

##### 第二步 添加对应接口的实现

```java
package com.github.rpc0.service.impl;

import com.github.rpc0.HelloService;
import io.vertx.core.Future;

/**
 * @author fishzhao
 * @since 2022-01-25
 */
public final class HelloServiceImpl implements HelloService {

  @Override
  public Future<String> sayHello(String name) {
    return Future.succeededFuture("Hello, " + name);
  }
}
```

此处新增一个实现了接口中对应方法的的对象，方法可能被多线程调用，需要编写者自行解决线程安全问题。 编写的方法需要符合Vert.x的约定，即避免阻塞EventLoop。

##### 第三步 构建一个Server实例并启动

```java
package com.github.rpc0;

import server.com.github.rpc0.Rpc0Server;
import server.com.github.rpc0.Rpc0ServerBuilder;
import service.com.github.rpc0.HelloService;
import com.github.rpc0.HelloServiceImpl;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServerOptions;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * @author fishzhao
 * @since 2022-01-20
 */
@Slf4j
public final class ExampleServer {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    Rpc0Server rpc0Server = new Rpc0ServerBuilder(vertx, new NetServerOptions().setHost(args[0]).setPort(Integer.parseInt(args[1])))
            .addBinding(HelloService.class, new HelloServiceImpl())
            .build();
    vertx.deployVerticle(rpc0Server)
            .onComplete(result -> {
              if (result.succeeded()) {
                log.info("Listening on: {}", Arrays.toString(args));
                Runtime.getRuntime().addShutdownHook(new Thread(() -> vertx.undeploy(result.result())));
              } else {
                log.error("Launch server with exception: ", result.cause());
              }
            });
  }
}

```

##### 第四步 客户端调用

```java
package com.github.rpc0;

import com.github.rpc0.client.ServiceFactory;
import com.github.rpc0.client.ServiceFactoryBuilder;
import com.github.rpc0.HelloService;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClientOptions;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author fishzhao
 * @since 2022-01-20
 */
public final class ExampleClient {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    ServiceFactory factory = new ServiceFactoryBuilder(
            vertx, "127.0.0.1", 9549,
            new NetClientOptions(),
            Duration.ofSeconds(3), Vertx.class.getClassLoader())
            .registerService(HelloService.class)
            .build();
    AtomicBoolean stopped = new AtomicBoolean(false);
    HelloService helloService = factory.create(HelloService.class);
    vertx.setPeriodic(1000, timeId -> {
      if (stopped.get()) {
        vertx.cancelTimer(timeId);
        return;
      }
      String s = UUID.randomUUID().toString();
      long start = System.currentTimeMillis();
      helloService.sayHello(s)
              .onComplete(result -> {
                if (result.succeeded()) {
                  System.out.printf("%s => %s: %dms%n", s, result.result(), System.currentTimeMillis() - start);
                } else {
                  result.cause().printStackTrace();
                }
              });
    });
    Runtime.getRuntime().addShutdownHook(new Thread(() -> stopped.set(true)));
  }
}


```

#### 支持的类型

##### 值类型

默认支持所有的基本类型、基本类型的数组以及下面的对象类型或其对应的数组：
`java.lang.Byte`, `java.lang.Boolean`, `java.lang.Character`, `java.lang.Short`, `java.lang.Integer`, `java.lang.Float`
, `java.lang.Double`, `java.lang.String`, `java.util.BitSet`, `java.net.URL`, `java.nio.charset.Charset`
, `java.util.Currency`, `java.math.BigInteger`, `java.math.BigDecimal`, `java.util.Date`, `java.util.Calendar`
, `java.util.TimeZone`, `java.time.LocalDate`, `java.time.LocalTime`, `java.time.LocalDateTime`
, `java.time.OffsetDateTime`, `java.time.ZonedDateTime`, `java.time.Duration`, `java.time.ZoneId`, `java.time.Instant`

#### 集合类型

集合类型建议声明（如方法签名、类型参数或字段属性）为接口类型，支持的接口类型如下：
`java.util.List`, `java.util.Set`, `java.util.SortedSet`, `java.util.Queue`, `java.util.Deque`, `java.util.Collection`
对于每种接口类型，rpc框架内部提供了默认的绑定实现，这样能保证最大程度的兼容性，当然，某些特定的场景下需要指定实现类型，目前支持以下类型的绑定：
`java.util.ArrayList`, `java.util.LinkedHashSet`, `java.util.HashSet`, `java.util.TreeSet`, `java.util.ArrayDeque`
, `java.util.PriorityQueue`
对于所有上述类型以外的类型，调用过程中会抛出异常

#### 字典类型

同集合类型，建议将Map声明为以下类型：
`java.util.Map`, `java.util.SortedMap`
同时支持以下类型的实现：
`java.util.HashMap`, `java.util.LinkedHashMap`, `java.util.TreeMap`, `java.util.Properties`

#### 自定义类型

自定义类型需要显式进行注册，以下为一个例子：

```java
package com.github.rpc0.model;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.github.rpc0.TrustedType;
import lombok.Getter;
import lombok.Setter;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @author fishzhao
 * @since 2022-01-17
 */
@Getter
@Setter
@TrustedType(typeId = 1)
public class User {

  private long id;
  private String name;
  private OffsetDateTime createTime;
  private List<String> tags;
  private int[] ints;
  private Map<String, Object> attributes;
  private Object[] objects;

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
            .omitNullValues()
            .add("id", id)
            .add("name", name)
            .add("createTime", createTime)
            .add("tags", tags)
            .add("ints", ints)
            .add("attributes", attributes)
            .add("objects", objects)
            .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    User user = (User) o;
    return id == user.id && Objects.equals(name, user.name) && Objects.equals(createTime, user.createTime) && Objects.equals(tags, user.tags) && Arrays.equals(ints, user.ints) && Objects.equals(attributes, user.attributes) && Arrays.equals(objects, user.objects);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(id, name, createTime, tags, attributes);
    result = 31 * result + Arrays.hashCode(ints);
    result = 31 * result + Arrays.hashCode(objects);
    return result;
  }
}

```

对于每一个实体，必须通过`@TrustedType(typeId = ${typeId})`显式指明唯一的typeId，并在初始化过程中，显式对其进行注册 或者自定义实现KryoRegistry接口来注册自定义类型
**注意：两者不可同时使用**

```java
Rpc0Server rpc0Server = new Rpc0ServerBuilder(vertx,
        new NetServerOptions().setHost(args[0])
        .setPort(Integer.parseInt(args[1])))
        .registerTypes("com.github.rpc0.model",false) // 通过扫描包的方式进行注册
        .build();
```

#### SSL/TLS支持

`vertx-rpc0`
可以通过Vert.x原生的机制支持消息加密，配置过程参考：[https://vertx.io/docs/vertx-core/java/#ssl](https://vertx.io/docs/vertx-core/java/#ssl)
