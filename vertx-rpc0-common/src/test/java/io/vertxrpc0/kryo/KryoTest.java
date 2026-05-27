package io.vertxrpc0.kryo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;

public class KryoTest {

  @Test
  public void selfReferentialGraphRoundTripsViaTrustedReferenceResolver() {
    Kryo kryo = new Kryo(new TrustedReferenceResolver());
    kryo.setReferences(true);
    kryo.register(Example.class);

    Example example = new Example();
    example.setName("111");
    example.setExample(example);

    Output output = new Output(256);
    kryo.writeObject(output, example);
    output.close();

    Example copy;
    try (Input input = new Input(output.toBytes())) {
      copy = kryo.readObject(input, Example.class);
    }

    assertEquals("111", copy.getName());
    assertSame(copy, copy.getExample(), "self-reference should be preserved");
  }

  @Setter
  @Getter
  public static final class Example {
    private String name;
    private Example example;
  }
}
