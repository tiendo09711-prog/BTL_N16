package vn.ptit.btl16.server.network;

import org.java_websocket.WebSocket;
import vn.ptit.btl16.common.protocol.JsonWireMessageCodec;
import vn.ptit.btl16.common.protocol.WireMessage;

import java.io.IOException;
import java.net.SocketException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WebSocketConnectionAdapter implements ServerConnection {
    private final String connectionId = UUID.randomUUID().toString();
    private final WebSocket socket;
    private final JsonWireMessageCodec codec;
    private final ConnectionRegistry registry;
    private final ConnectionLifecycleListener lifecycleListener;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile Instant lastReadAt = Instant.now();
    private volatile Instant lastWriteAt = Instant.now();

    public WebSocketConnectionAdapter(
            WebSocket socket,
            JsonWireMessageCodec codec,
            ConnectionRegistry registry,
            ConnectionLifecycleListener lifecycleListener) {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.lifecycleListener = Objects.requireNonNull(lifecycleListener, "lifecycleListener");
    }

    public void markRead() { lastReadAt = Instant.now(); }

    @Override
    public void send(WireMessage message) throws IOException {
        if (closed.get() || !socket.isOpen()) {
            throw new SocketException("WebSocket connection is closed");
        }
        socket.send(codec.encode(message));
        lastWriteAt = Instant.now();
    }

    @Override
    public String getConnectionId() { return connectionId; }
    @Override
    public String getRemoteAddress() { return String.valueOf(socket.getRemoteSocketAddress()); }
    @Override
    public String getTransportName() { return "WEBSOCKET"; }
    @Override
    public boolean isClosed() { return closed.get() || socket.isClosed(); }
    @Override
    public Instant getLastReadAt() { return lastReadAt; }
    @Override
    public Instant getLastWriteAt() { return lastWriteAt; }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        registry.remove(connectionId);
        try {
            lifecycleListener.onDisconnected(this);
        } catch (RuntimeException exception) {
            System.err.println("[WS] Disconnect listener error: " + exception.getMessage());
        }
        if (socket.isOpen()) {
            socket.close();
        }
        System.out.println("[WS] Disconnected " + connectionId);
    }
}
