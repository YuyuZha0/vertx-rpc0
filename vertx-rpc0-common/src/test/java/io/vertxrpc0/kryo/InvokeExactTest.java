package io.vertxrpc0.kryo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.junit.jupiter.api.Test;

/**
 * @author fishzhao
 * @since 2022-02-24
 */
public class InvokeExactTest {

  @Test
  public void test() throws Throwable {
    MethodHandle methodHandle = MethodHandles.publicLookup()
            .findVirtual(CharSequence.class, "length", MethodType.methodType(int.class));
    String s = "Hello World";
    assertEquals(s.length(), methodHandle.invoke(s));
  }
}
