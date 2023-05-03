package com.github.rpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ImmutableSerializer;
import com.github.rpc0.invoke.InvokeResult;
import com.github.rpc0.invoke.ResultCode;

/**
 * @author fishzhao
 * @since 2021-12-16
 */
public final class InvokeResultSerializer extends ImmutableSerializer<InvokeResult> {


  @Override
  public void write(Kryo kryo, Output output, InvokeResult object) {
    output.writeLong(object.getRequestId(), true);
    output.writeLong(object.getTimestamp(), true);
    output.writeInt(object.getCode().ordinal(), true);
    output.writeString(object.getErrorMessage());
    kryo.writeClassAndObject(output, object.getResult());
  }

  @Override
  public InvokeResult read(Kryo kryo, Input input, Class<? extends InvokeResult> type) {
    return new InvokeResult(
            input.readLong(true),
            input.readLong(true),
            ResultCode.forCode(input.readInt(true)),
            input.readString(),
            kryo.readClassAndObject(input)
    );
  }

}
