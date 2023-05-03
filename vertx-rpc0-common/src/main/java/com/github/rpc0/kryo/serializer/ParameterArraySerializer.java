package com.github.rpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ImmutableSerializer;
import com.github.rpc0.invoke.ParameterArray;

/**
 * @author fishzhao
 * @since 2021-12-22
 */
public final class ParameterArraySerializer extends ImmutableSerializer<ParameterArray> {

  @Override
  public void write(Kryo kryo, Output output, ParameterArray object) {
    output.writeInt(object.size() + 1, true);
    for (Object param : object) {
      kryo.writeClassAndObject(output, param);
    }
  }

  @Override
  public ParameterArray read(Kryo kryo, Input input, Class<? extends ParameterArray> type) {
    int size = input.readInt(true) - 1;
    if (size == 0) {
      return ParameterArray.create();
    }
    Object[] parameters = new Object[size];
    for (int i = 0; i < parameters.length; ++i) {
      parameters[i] = kryo.readClassAndObject(input);
    }
    return ParameterArray.create(parameters);
  }

}
