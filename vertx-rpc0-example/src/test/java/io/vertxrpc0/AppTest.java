package io.vertxrpc0;

import com.google.common.collect.ImmutableMap;
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
import org.junit.jupiter.api.Timeout;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
public class AppTest {

  private Rpc0TestHarness harness;
  private ServiceFactory factory;

  @BeforeEach
  public void setUp() throws Exception {
    harness = Rpc0TestHarness.start(true);
    factory = harness.factory();
  }

  @AfterEach
  public void tearDown() throws Exception {
    harness.close();
  }

  private static <T> T await(io.vertx.core.Future<T> f) throws Exception {
    return f.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
  }

  @Test
  public void doubleServiceAddsPair() throws Exception {
    DoubleService doubleService = factory.create(DoubleService.class);
    assertEquals(9D, await(doubleService.add(1D, 8D)));
  }

  @Test
  public void doubleServiceAddsArray() throws Exception {
    DoubleService doubleService = factory.create(DoubleService.class);
    assertEquals(15D, await(doubleService.add(new Double[]{1D, 2D, 3D, 4D, 5D})));
  }

  @Test
  public void stringServiceSplit() throws Exception {
    StringService stringService = factory.create(StringService.class);
    String uuid = UUID.randomUUID().toString();
    assertEquals(Arrays.asList(uuid.split("-")), await(stringService.split(uuid, '-')));
  }

  @Test
  public void stringServiceQueryParamWithoutQuery() throws Exception {
    StringService stringService = factory.create(StringService.class);
    assertTrue(await(stringService.getUrlQueryParam("http://example.com")).isEmpty());
    assertTrue(await(stringService.getUrlQueryParam(null)).isEmpty());
  }

  @Test
  public void stringServiceQueryParamWithQuery() throws Exception {
    StringService stringService = factory.create(StringService.class);
    assertEquals(ImmutableMap.of("a", "111", "b", "222", "c", "hello"),
            await(stringService.getUrlQueryParam("http://example.com?a=111&b=222&c=hello")));
  }

  @Test
  public void timeServiceNullArgPropagatesError() {
    TimeService timeService = factory.create(TimeService.class);
    CompletableFuture<LocalDateTime> cf = timeService.timeAfterNDays(null, 1)
            .toCompletionStage().toCompletableFuture();
    Throwable cause = assertThrowsExecution(cf);
    assertNotNull(cause);
  }

  private static Throwable assertThrowsExecution(CompletableFuture<?> cf) {
    try {
      cf.get(10, TimeUnit.SECONDS);
      throw new AssertionError("expected failure");
    } catch (java.util.concurrent.ExecutionException e) {
      return e.getCause();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void timeServiceAdvanceAndDuration() throws Exception {
    TimeService timeService = factory.create(TimeService.class);
    LocalDateTime time1 = LocalDateTime.now();
    LocalDateTime time2 = time1.plusDays(100);
    assertEquals(time2, await(timeService.timeAfterNDays(time1, 100)));
    assertEquals(TimeUnit.DAYS.toMillis(100), (long) await(timeService.durationMills(time1, time2)));
  }

  @Test
  public void voidServiceRun() throws Exception {
    VoidService voidService = factory.create(VoidService.class);
    await(voidService.run());
  }

  @Test
  public void beanServiceRoundTrip() throws Exception {
    BeanService beanService = factory.create(BeanService.class);
    User user = User.generate();
    String json = await(beanService.serializeToJson(user));
    assertEquals(harness.objectMapper().writeValueAsString(user), json);
  }
}
