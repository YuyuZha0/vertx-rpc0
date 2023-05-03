package com.github.rpc0.reflection;

import com.esotericsoftware.kryo.Kryo;
import com.github.rpc0.model.User;
import org.junit.Test;

/**
 * @author fishzhao
 * @since 2022-01-18
 */
public class FieldAccessTest {

  @Test
  public void test() {
    Kryo kryo = new Kryo();
    kryo.setRegistrationRequired(false);
    User user = User.generate();
    User user1 = kryo.copyShallow(user);
    System.out.println(user1);
  }
}
