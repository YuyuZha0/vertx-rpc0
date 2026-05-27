package io.vertxrpc0.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.common.collect.ImmutableMap;
import io.vertx.core.Future;
import io.vertxrpc0.invoke.InvokeSpec;
import io.vertxrpc0.invoke.ParameterArray;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import org.junit.jupiter.api.Test;

public class ServiceLookupTest {

  @Test
  public void unknownServiceReturnsNull() {
    ServiceLookup lookup = new ServiceLookup(ImmutableMap.of());
    InvokeSpec spec = new InvokeSpec(1, 0L, "nope", "doNothing",
            MethodType.methodType(Future.class), ParameterArray.create());
    assertNull(lookup.lookup(spec));
  }

  @Test
  public void knownServiceReturnsBoundMethodHandle() throws Throwable {
    Echo impl = new EchoImpl();
    ServiceLookup lookup = new ServiceLookup(ImmutableMap.of(Echo.class.getTypeName(), impl));
    InvokeSpec spec = new InvokeSpec(1, 0L, Echo.class.getTypeName(), "echo",
            MethodType.methodType(String.class, String.class),
            ParameterArray.create(new Object[]{"hello"}));

    MethodHandle mh = lookup.lookup(spec);
    assertNotNull(mh);

    @SuppressWarnings("unchecked")
    Future<String> ret = (Future<String>) mh.invokeWithArguments("hello");
    assertEquals("hello", ret.result());
  }

  public interface Echo {
    Future<String> echo(String input);
  }

  public static final class EchoImpl implements Echo {
    @Override
    public Future<String> echo(String input) {
      return Future.succeededFuture(input);
    }
  }
}
