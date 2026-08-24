package vn.ptit.btl16.client.network;

import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.WireMessage;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface ClientTransport extends AutoCloseable {
    CompletableFuture<Void> connect();
    void disconnect();
    boolean isConnected();
    ConnectionState getState();
    String getEndpoint();
    void setEndpoint(String endpoint);

    CompletableFuture<WireMessage> sendRequest(
            MessageType type,
            Map<String, String> data);

    CompletableFuture<WireMessage> ping();
    void addEventListener(Consumer<WireMessage> listener);
    void addStateListener(ConnectionStateListener listener);

    @Override
    void close();
}
