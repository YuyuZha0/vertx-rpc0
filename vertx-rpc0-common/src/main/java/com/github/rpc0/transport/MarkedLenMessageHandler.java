package com.github.rpc0.transport;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.netty.buffer.ByteBufUtil;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.parsetools.RecordParser;
import lombok.NonNull;


/**
 * @author fishzhao
 * @since 2021-12-16
 */
public final class MarkedLenMessageHandler implements Handler<Buffer> {

  private static final int DEFAULT_MAX_MSG_LEN = 10 << 20; //10MB

  private final ParserHandler parserHandler;
  private final RecordParser recordParser;
  private final int maxMsgLen;
  private boolean expectLen = true;

  public MarkedLenMessageHandler(@NonNull ParserHandler parserHandler, int maxMsgLen) {
    Preconditions.checkArgument(maxMsgLen > 0, "Illegal maxMsgLen: %s", maxMsgLen);
    this.parserHandler = parserHandler;
    this.recordParser = RecordParser.newFixed(Prefix.prefixLen()).handler(this::handleProtocol);
    this.maxMsgLen = maxMsgLen;
  }

  public MarkedLenMessageHandler(ParserHandler parserHandler) {
    this(parserHandler, DEFAULT_MAX_MSG_LEN);
  }

  @Override
  public void handle(Buffer event) {
    recordParser.handle(event);
  }

  private void handleProtocol(Buffer event) {
    try {
      if (isExpectLen()) {
        byte[] magic = event.getBytes(0, 2);
        if (!Prefix.isMagicMatch(magic)) {
          parserHandler.fetal(new VertxException(
                  Strings.lenientFormat("Unknown protocol magic: %s", ByteBufUtil.hexDump(magic)
                  ), true));
          return;
        }
        int msgLen = event.getInt(2);
        if (msgLen < 0 || msgLen > maxMsgLen) {
          parserHandler.fetal(new VertxException(
                  Strings.lenientFormat("Invalid msgLen: %s", msgLen
                  ), true));
          return;
        }
        recordParser.fixedSizeMode(msgLen);
        setExpectLen(false);
      } else {
        parserHandler.handle(event);
        recordParser.fixedSizeMode(Prefix.prefixLen());
        setExpectLen(true);
      }
    } catch (Exception e) {
      parserHandler.fetal(e);
    }
  }

  private boolean isExpectLen() {
    return expectLen;
  }

  private void setExpectLen(boolean expectLen) {
    this.expectLen = expectLen;
  }
}
