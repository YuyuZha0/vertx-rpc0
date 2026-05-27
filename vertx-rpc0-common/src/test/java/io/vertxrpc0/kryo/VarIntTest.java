package io.vertxrpc0.kryo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

public class VarIntTest {

  @Test
  public void roundTripVarIntFlagWithFalseFlag() {
    byte[] bytes;
    ByteArrayOutputStream out = new ByteArrayOutputStream(64);
    try (Output output = new Output(out)) {
      output.writeVarIntFlag(false, 0xffff, true);
      output.flush();
      bytes = out.toByteArray();
    }
    try (Input input = new Input(bytes)) {
      assertFalse(input.readVarIntFlag(), "flag bit should round-trip as false");
      assertEquals(0xffff, input.readVarIntFlag(true));
    }
  }

  @Test
  public void roundTripVarIntFlagWithTrueFlag() {
    byte[] bytes;
    ByteArrayOutputStream out = new ByteArrayOutputStream(64);
    try (Output output = new Output(out)) {
      output.writeVarIntFlag(true, 42, true);
      output.flush();
      bytes = out.toByteArray();
    }
    try (Input input = new Input(bytes)) {
      assertTrue(input.readVarIntFlag());
      assertEquals(42, input.readVarIntFlag(true));
    }
  }
}
