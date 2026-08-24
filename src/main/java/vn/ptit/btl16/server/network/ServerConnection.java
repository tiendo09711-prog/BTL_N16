package vn.ptit.btl16.server.network;

import vn.ptit.btl16.common.protocol.WireMessage;

import java.io.IOException;
import java.time.Instant;

public interface ServerConnection extends AutoCloseable {
    String getConnectionId();
    String getRemoteAddress();
    String getTransportName();
    boolean isClosed();
    Instant getLastReadAt();
    Instant getLastWriteAt();
    void send(WireMessage message) throws IOException;

    @Override
    void close();
}
