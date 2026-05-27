package io.vertxrpc0.reflection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.esotericsoftware.kryo.Kryo;
import io.vertxrpc0.model.User;
import org.junit.jupiter.api.Test;

public class FieldAccessTest {

  @Test
  public void shallowCopyProducesEqualButNotSameInstance() {
    Kryo kryo = new Kryo();
    kryo.setRegistrationRequired(false);

    User user = User.generate();
    User copy = kryo.copyShallow(user);

    assertNotSame(user, copy);
    assertEquals(user, copy);
  }
}
