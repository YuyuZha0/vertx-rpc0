package io.vertxrpc0;

import com.google.common.collect.ImmutableMap;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertxrpc0.client.ServiceFactory;
import io.vertxrpc0.model.User;
import io.vertxrpc0.service.BeanService;
import io.vertxrpc0.service.DoubleService;
import io.vertxrpc0.service.StringService;
import io.vertxrpc0.service.TimeService;
import io.vertxrpc0.service.VoidService;
import io.vertxrpc0.testutil.Rpc0TestHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
@Timeout(value = 30, timeUnit = TimeUnit.SECONDS)
public class AppTest {

  private Rpc0TestHarness harness;
  private ServiceFactory factory;

  @BeforeEach
  public void setUp(Vertx vertx, VertxTestContext ctx) {
    Rpc0TestHarness.start(vertx, true).onComplete(ctx.succeeding(h -> {
      harness = h;
      factory = h.factory();
      ctx.completeNow();
    }));
  }

  @AfterEach
  public void tearDown(VertxTestContext ctx) {
    harness.close().onComplete(ctx.succeedingThenComplete());
  }

  @Test
  public void doubleServiceAddsPair(VertxTestContext ctx) {
    DoubleService doubleService = factory.create(DoubleService.class);
    doubleService.add(1D, 8D).onComplete(ctx.succeeding(result -> {
      ctx.verify(() -> assertEquals(9D, result));
      ctx.completeNow();
    }));
  }

  @Test
  public void doubleServiceAddsArray(VertxTestContext ctx) {
    DoubleService doubleService = factory.create(DoubleService.class);
    doubleService.add(new Double[]{1D, 2D, 3D, 4D, 5D}).onComplete(ctx.succeeding(result -> {
      ctx.verify(() -> assertEquals(15D, result));
      ctx.completeNow();
    }));
  }

  @Test
  public void stringServiceSplit(VertxTestContext ctx) {
    StringService stringService = factory.create(StringService.class);
    String uuid = UUID.randomUUID().toString();
    stringService.split(uuid, '-').onComplete(ctx.succeeding(result -> {
      ctx.verify(() -> assertEquals(Arrays.asList(uuid.split("-")), result));
      ctx.completeNow();
    }));
  }

  @Test
  public void stringServiceQueryParamWithoutQuery(VertxTestContext ctx) {
    StringService stringService = factory.create(StringService.class);
    Checkpoint done = ctx.checkpoint(2);
    stringService.getUrlQueryParam("http://example.com").onComplete(ctx.succeeding(result -> {
      ctx.verify(() -> assertTrue(result.isEmpty()));
      done.flag();
    }));
    stringService.getUrlQueryParam(null).onComplete(ctx.succeeding(result -> {
      ctx.verify(() -> assertTrue(result.isEmpty()));
      done.flag();
    }));
  }

  @Test
  public void stringServiceQueryParamWithQuery(VertxTestContext ctx) {
    StringService stringService = factory.create(StringService.class);
    stringService.getUrlQueryParam("http://example.com?a=111&b=222&c=hello")
            .onComplete(ctx.succeeding(result -> {
              ctx.verify(() ->
                      assertEquals(ImmutableMap.of("a", "111", "b", "222", "c", "hello"), result));
              ctx.completeNow();
            }));
  }

  @Test
  public void timeServiceNullArgPropagatesError(VertxTestContext ctx) {
    TimeService timeService = factory.create(TimeService.class);
    timeService.timeAfterNDays(null, 1).onComplete(ctx.failing(cause -> {
      ctx.verify(() -> assertNotNull(cause));
      ctx.completeNow();
    }));
  }

  @Test
  public void timeServiceAdvanceAndDuration(VertxTestContext ctx) {
    TimeService timeService = factory.create(TimeService.class);
    LocalDateTime time1 = LocalDateTime.now();
    LocalDateTime time2 = time1.plusDays(100);
    Checkpoint done = ctx.checkpoint(2);
    timeService.timeAfterNDays(time1, 100).onComplete(ctx.succeeding(result -> {
      ctx.verify(() -> assertEquals(time2, result));
      done.flag();
    }));
    timeService.durationMills(time1, time2).onComplete(ctx.succeeding(result -> {
      ctx.verify(() -> assertEquals(TimeUnit.DAYS.toMillis(100), (long) result));
      done.flag();
    }));
  }

  @Test
  public void voidServiceRun(VertxTestContext ctx) {
    VoidService voidService = factory.create(VoidService.class);
    voidService.run().onComplete(ctx.succeedingThenComplete());
  }

  @Test
  public void beanServiceRoundTrip(VertxTestContext ctx) {
    BeanService beanService = factory.create(BeanService.class);
    User user = User.generate();
    beanService.serializeToJson(user).onComplete(ctx.succeeding(json -> {
      ctx.verify(() -> assertEquals(harness.objectMapper().writeValueAsString(user), json));
      ctx.completeNow();
    }));
  }
}
