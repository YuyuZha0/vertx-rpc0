package com.github.rpc0;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rpc0.client.ServiceFactory;
import com.github.rpc0.client.ServiceFactoryBuilder;
import com.github.rpc0.model.User;
import com.github.rpc0.server.Rpc0Server;
import com.github.rpc0.server.Rpc0ServerBuilder;
import com.github.rpc0.service.BeanService;
import com.github.rpc0.service.DoubleService;
import com.github.rpc0.service.StringService;
import com.github.rpc0.service.TimeService;
import com.github.rpc0.service.VoidService;
import com.github.rpc0.service.impl.BeanServiceImpl;
import com.github.rpc0.service.impl.DoubleServiceImpl;
import com.github.rpc0.service.impl.StringServiceImpl;
import com.github.rpc0.service.impl.TimeServiceImpl;
import com.github.rpc0.service.impl.VoidServiceImpl;
import com.github.rpc0.util.ObjectMapperSupplier;
import com.google.common.collect.ImmutableMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@RunWith(VertxUnitRunner.class)
@Slf4j
public class AppTest {

    private static final String FACTORY_KEY = "__factory";
    private final ObjectMapper objectMapper = new ObjectMapperSupplier().get();

    @Rule
    public RunTestOnContext rule = new RunTestOnContext();

    @Before
    public void before(TestContext context) {
        Vertx vertx = rule.vertx();
        SelfSignedCertificate certificate = SelfSignedCertificate.create();
        Rpc0Server rpc0Server = new Rpc0ServerBuilder(vertx, new NetServerOptions().setHost("127.0.0.1").setPort(9999).setSsl(true).setTrustOptions(certificate.trustOptions()).setKeyCertOptions(certificate.keyCertOptions())).addBinding(StringService.class, new StringServiceImpl()).addBinding(DoubleService.class, new DoubleServiceImpl()).addBinding(TimeService.class, new TimeServiceImpl()).addBinding(VoidService.class, new VoidServiceImpl(vertx)).addBinding(BeanService.class, new BeanServiceImpl(objectMapper)).registerTypes("com.github.rpc0.model", false).build();
        Promise<Void> promise = Promise.promise();
        vertx.deployVerticle(rpc0Server).onSuccess(deploymentId -> {
            ServiceFactory factory = new ServiceFactoryBuilder(vertx, "127.0.0.1", 9999, new NetClientOptions().setSsl(true).setKeyCertOptions(certificate.keyCertOptions()).setTrustOptions(certificate.trustOptions()), Duration.ofSeconds(3), Vertx.class.getClassLoader()).registerService(DoubleService.class).registerService(StringService.class).registerService(TimeService.class).registerService(VoidService.class).registerService(BeanService.class).registerTypes("com.github.rpc0.model", false).build();

            context.put(FACTORY_KEY, factory);
            promise.complete();
        }).onFailure(promise::tryFail);
        promise.future().onComplete(context.asyncAssertSuccess());
    }

    @After
    public void after(TestContext context) {
        rule.vertx().close(context.asyncAssertSuccess());
    }

    @Test
    public void testDoubleService(TestContext context) {
        Async async = context.async(2);
        ServiceFactory factory = context.get(FACTORY_KEY);
        DoubleService doubleService = factory.create(DoubleService.class);
        doubleService.add(1D, 8D).onComplete(result -> {
            context.assertTrue(result.succeeded());
            context.assertEquals(9D, result.result());
            async.countDown();
        });
        doubleService.add(new Double[]{1D, 2D, 3D, 4D, 5D}).onComplete(result -> {
            context.assertTrue(result.succeeded());
            context.assertEquals(15D, result.result());
            async.countDown();
        });
    }

    @Test
    public void testStringService(TestContext context) {
        Async async = context.async(4);
        ServiceFactory factory = context.get(FACTORY_KEY);
        StringService stringService = factory.create(StringService.class);
        String uuid = UUID.randomUUID().toString();
        stringService.split(uuid, '-').onComplete(result -> {
            context.assertTrue(result.succeeded());
            context.assertEquals(Arrays.asList(uuid.split("-")), result.result());
            async.countDown();
        });
        stringService.getUrlQueryParam(null).onComplete(result -> {
            context.assertTrue(result.succeeded());
            context.assertTrue(result.result().isEmpty());
            async.countDown();
        });
        stringService.getUrlQueryParam("http://exapmle.com").onComplete(result -> {
            context.assertTrue(result.succeeded());
            context.assertTrue(result.result().isEmpty());
            async.countDown();
        });
        stringService.getUrlQueryParam("http://example.com?a=111&b=222&c=hello").onComplete(result -> {
            context.assertTrue(result.succeeded());
            context.assertEquals(ImmutableMap.of("a", "111", "b", "222", "c", "hello"), result.result());
            async.countDown();
        });
    }

    @Test
    public void testTimeService(TestContext context) {
        Async async = context.async(3);
        ServiceFactory factory = context.get(FACTORY_KEY);
        TimeService timeService = factory.create(TimeService.class);
        timeService.timeAfterNDays(null, 123).onComplete(result -> {
            context.assertTrue(result.failed());
            result.cause().printStackTrace();
            async.countDown();
        });

        LocalDateTime time1 = LocalDateTime.now();
        LocalDateTime time2 = time1.plusDays(100);
        timeService.timeAfterNDays(time1, 100).onComplete(result -> {
            context.assertTrue(result.succeeded());
            context.assertEquals(time2, result.result());
            async.countDown();
        });
        timeService.durationMills(time1, time2).onComplete(result -> {
            context.assertTrue(result.succeeded());
            context.assertEquals(TimeUnit.DAYS.toMillis(100), result.result());
            async.countDown();
        });
    }

    @Test
    public void testVoidService(TestContext context) {
        Async async = context.async(1);
        ServiceFactory factory = context.get(FACTORY_KEY);
        VoidService voidService = factory.create(VoidService.class);
        voidService.run().onComplete(result -> {
            context.assertTrue(result.succeeded());
            async.countDown();
        });
    }

    @Test
    public void testBeanService(TestContext context) {
        Async async = context.async();
        ServiceFactory factory = context.get(FACTORY_KEY);
        BeanService beanService = factory.create(BeanService.class);
        User user = User.generate();

        beanService.serializeToJson(user).onComplete(result -> {
            if (result.failed()) {
                result.cause().printStackTrace();
            }
            context.assertTrue(result.succeeded());
            String json = result.result();
            System.out.println(json);
            try {
                context.assertEquals(objectMapper.writeValueAsString(user), json);
            } catch (JsonProcessingException e) {
                context.fail(e);
            }
            async.countDown();
        });
    }
}
