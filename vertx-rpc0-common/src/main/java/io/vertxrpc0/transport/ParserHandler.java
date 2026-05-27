package io.vertxrpc0.transport;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

/**
 * @author fishzhao
 * @since 2021-12-16
 */
public interface ParserHandler extends Handler<Buffer> {

  void fatal(Throwable cause);
}
