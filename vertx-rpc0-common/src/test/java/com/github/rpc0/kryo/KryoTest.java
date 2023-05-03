package com.github.rpc0.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import lombok.Getter;
import lombok.Setter;
import org.junit.Test;

/**
 * @author fishzhao
 * @since 2021-12-22
 */
public class KryoTest {

  @Test
  public void test() {
    Kryo kryo = new Kryo(new TrustedReferenceResolver());
    kryo.register(Example.class);

    Example example = new Example();
    example.setName("111");
    example.setExample(example);

    Output output = new Output(256);
    kryo.writeObject(output, example);
  }


  @Setter
  @Getter
  public static final class Example {
    private String name;
    private Example example;
  }
}
