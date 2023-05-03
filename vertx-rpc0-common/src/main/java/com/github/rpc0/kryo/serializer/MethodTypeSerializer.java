package com.github.rpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ImmutableSerializer;
import com.google.common.primitives.Primitives;

import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * @author fishzhao
 * @since 2021-12-13
 */
public final class MethodTypeSerializer extends ImmutableSerializer<MethodType> {

  @Override
  public void write(Kryo kryo, Output output, MethodType methodType) {
    int parameterCnt = methodType.parameterCount();
    BitSet isPrimitiveAt = new BitSet(parameterCnt + 1);
    isPrimitiveAt.set(0, methodType.returnType().isPrimitive());
    for (int i = 0; i < parameterCnt; ++i) {
      isPrimitiveAt.set(i + 1, methodType.parameterType(i).isPrimitive());
    }
    output.writeInt(parameterCnt, true);
    byte[] bytes = isPrimitiveAt.toByteArray();
    output.writeInt(bytes.length + 1, true);
    output.writeBytes(bytes);
    kryo.writeClass(output, methodType.returnType());
    for (int i = 0; i < parameterCnt; ++i) {
      kryo.writeClass(output, methodType.parameterType(i));
    }
  }

  @Override
  public MethodType read(Kryo kryo, Input input, Class<? extends MethodType> aClass) {
    int parameterCnt = input.readInt(true);
    int bytesLen = input.readInt(true) - 1;
    BitSet isPrimitiveAt = BitSet.valueOf(input.readBytes(bytesLen));
    Class<?> returnType = kryo.readClass(input).getType();
    if (parameterCnt == 0) {
      return MethodType.methodType(wrap(returnType, isPrimitiveAt.get(0)));
    }
    List<Class<?>> parameterTypes = new ArrayList<>(parameterCnt);
    for (int i = 0; i < parameterCnt; ++i) {
      parameterTypes.add(wrap(kryo.readClass(input).getType(), isPrimitiveAt.get(i + 1)));
    }
    return MethodType.methodType(wrap(returnType, isPrimitiveAt.get(0)), parameterTypes);
  }

  private Class<?> wrap(Class<?> type, boolean isPrimitive) {
    if (isPrimitive) {
      return Primitives.unwrap(type);
    } else {
      return Primitives.wrap(type);
    }
  }

}
