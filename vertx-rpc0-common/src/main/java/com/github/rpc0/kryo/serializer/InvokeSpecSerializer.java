package com.github.rpc0.kryo.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ImmutableSerializer;
import com.github.rpc0.invoke.InvokeSpec;
import com.github.rpc0.invoke.ParameterArray;

import java.lang.invoke.MethodType;

/**
 * @author fishzhao
 * @since 2021-12-14
 */
public final class InvokeSpecSerializer extends ImmutableSerializer<InvokeSpec> {

  @Override
  public void write(Kryo kryo, Output output, InvokeSpec spec) {
    output.writeLong(spec.getRequestId(), true);
    output.writeLong(spec.getTimestamp(), true);
    output.writeString(spec.getCallSiteClassName());
    output.writeString(spec.getMethodName());
    kryo.writeObject(output, spec.getMethodType());
    kryo.writeObject(output, spec.getParameters());
  }

  @Override
  public InvokeSpec read(Kryo kryo, Input input, Class<? extends InvokeSpec> type) {

    long requestId = input.readLong(true);
    long timestamp = input.readLong(true);
    String callSiteClassName = input.readString();
    String methodName = input.readString();
    MethodType methodType = kryo.readObject(input, MethodType.class);
    ParameterArray parameterArray = kryo.readObject(input, ParameterArray.class);
    return new InvokeSpec(
            requestId,
            timestamp,
            callSiteClassName,
            methodName,
            methodType,
            parameterArray
    );
  }

}
