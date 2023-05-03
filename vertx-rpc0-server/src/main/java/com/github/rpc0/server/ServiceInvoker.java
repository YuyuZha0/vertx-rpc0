package com.github.rpc0.server;

import com.esotericsoftware.kryo.KryoException;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.github.rpc0.invoke.InvokeResult;
import com.github.rpc0.invoke.InvokeSpec;
import com.github.rpc0.invoke.ResultCode;
import com.github.rpc0.transport.MarkedLenMessageHandler;
import com.github.rpc0.transport.MessageTransport;
import com.github.rpc0.transport.ParserHandler;
import com.github.rpc0.transport.Prefix;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import io.netty.buffer.ByteBuf;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.NetSocketInternal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author fishzhao
 * @since 2021-12-20
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class ServiceInvoker implements ParserHandler {

  private final TLongSet acceptedRequestIdSet = new TLongHashSet();
  private final AtomicLong lastActiveTime = new AtomicLong(System.currentTimeMillis());
  @Getter(AccessLevel.PACKAGE)
  private final NetSocketInternal socket;
  private final MessageTransport messageTransport;
  private final ServiceLookup serviceLookup;

  private static String buildErrorMessage(Throwable cause) {
    return Strings.lenientFormat("%s(\"%s\")",
            cause.getClass().getTypeName(), Throwables.getRootCause(cause).getMessage());
  }

  void registerHandlers(Runnable dispose) {
    socket.handler(new MarkedLenMessageHandler(this));
    SocketAddress socketAddress = socket.remoteAddress();
    socket.closeHandler(v -> {
      lastActiveTime.set(-1L);
      if (dispose != null) {
        try {
          dispose.run();
        } catch (Exception ignore) {
        }
      }
      log.info("Close connection with: {}", socketAddress);
    });
  }

  @Override
  public void fetal(Throwable cause) {
    log.error("Fetal error on [{}]: ", socket.remoteAddress(), cause);
    ResultCode resultCode = cause instanceof KryoException
            ? ResultCode.PROTOCOL_ERROR : ResultCode.UNKNOWN_ERROR;
    if (acceptedRequestIdSet.isEmpty()) {
      socket.close();
      return;
    }
    long time = System.currentTimeMillis();
    String msg = buildErrorMessage(cause);
    @SuppressWarnings("rawtypes") List<Future> futures = new ArrayList<>();
    for (long requestId : acceptedRequestIdSet.toArray()) {
      InvokeResult result = new InvokeResult(
              requestId,
              time,
              resultCode,
              msg,
              null
      );
      Promise<Void> promise = Promise.promise();
      writeResult(result, promise);
      futures.add(promise.future());
    }
    CompositeFuture.join(futures)
            .onComplete(ar -> socket.close());
  }

  // This method associated with the same event-loop, so it's thread-safe
  @Override
  public void handle(Buffer event) {
    InvokeSpec invokeSpec = (InvokeSpec) messageTransport.deserialize(event.getByteBuf());
    long requestId = invokeSpec.getRequestId();
    if (!invokeSpec.getParameters().isTypeMatch(invokeSpec.getMethodType())) {
      fail(requestId, ResultCode.PARAMETER_ERROR,
              "Parameter type not match: %s, %s", invokeSpec.getMethodType(), invokeSpec.getParameters()
      );
      return;
    }
    if (!acceptedRequestIdSet.add(requestId)) {
      fail(requestId, ResultCode.PARAMETER_ERROR,
              "Duplicated requestId: %s", requestId);
      return;
    }
    MethodHandle methodHandle;
    try {
      methodHandle = serviceLookup.lookup(invokeSpec);
    } catch (Exception e) {
      fail(requestId,
              ResultCode.LOOKUP_ERROR,
              "Exception while lookup method: %s", e.getMessage());
      return;
    }
    if (methodHandle == null) {
      fail(requestId,
              ResultCode.LOOKUP_ERROR,
              "Method not found: \"%s\"", invokeSpec.getMethodName());
      return;
    }
    try {
      @SuppressWarnings("unchecked")
      Future<Object> future = (Future<Object>) methodHandle
              .invokeWithArguments(invokeSpec.getParameters());
      future.onComplete(result -> {
        if (result.succeeded()) {
          Object ret = result.result();
          Class<?> resultType = invokeSpec.getMethodType().returnType();
          if (ret != null
              && !(resultType.isInstance(ret))) {
            fail(requestId, ResultCode.INVOCATION_ERROR,
                    "Actual result type not match, required %s, but found: %s", resultType, ret.getClass());
            return;
          }
          success(requestId, ret);
        } else {
          fail(requestId, ResultCode.INVOCATION_ERROR, result.cause());
        }
      });
    } catch (Throwable cause) {
      fail(requestId, ResultCode.UNKNOWN_ERROR, cause);
    }
  }

  private void success(long requestId, Object object) {
    InvokeResult invokeResult = new InvokeResult(
            requestId,
            System.currentTimeMillis(),
            ResultCode.OK,
            null,
            object
    );
    writeResult(invokeResult, null);
  }

  private void fail(long requestId, ResultCode code, Throwable cause) {
    InvokeResult invokeResult = new InvokeResult(
            requestId,
            System.currentTimeMillis(),
            code,
            buildErrorMessage(cause),
            null
    );
    writeResult(invokeResult, null);
  }

  private void fail(long requestId, ResultCode code, String template, Object... args) {
    InvokeResult invokeResult = new InvokeResult(
            requestId,
            System.currentTimeMillis(),
            code,
            Strings.lenientFormat(template, args),
            null
    );
    writeResult(invokeResult, null);
  }

  private void writeResult(InvokeResult result, Promise<Void> promise) {
    lastActiveTime.set(result.getTimestamp());
    ByteBuf byteBuf = Prefix.prependTo(messageTransport
            .serialize(socket.channelHandlerContext().alloc(), result));
    socket.write(Buffer.buffer(byteBuf), ar -> {
      //On the socket context, it's thread-safe
      acceptedRequestIdSet.remove(result.getRequestId());
      if (promise != null) {
        promise.handle(ar);
      }
    });
  }

  long lastActiveTime() {
    return lastActiveTime.get();
  }
}
