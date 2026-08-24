package vn.ptit.btl16.server.network;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.common.protocol.JsonWireMessageCodec;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.WireMessage;
import vn.ptit.btl16.server.ServerSequence;
import vn.ptit.btl16.server.routing.MessageRouter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WebSocketServerTransport extends WebSocketServer implements AutoCloseable {
    private final String path;
    private final JsonWireMessageCodec codec;
    private final MessageRouter router;
    private final ConnectionRegistry registry;
    private final ConnectionLifecycleListener lifecycleListener;
    private final ServerSequence sequence;
    private final ConcurrentHashMap<WebSocket, WebSocketConnectionAdapter> adapters =
            new ConcurrentHashMap<>();
    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch terminated = new CountDownLatch(1);
    private final AtomicBoolean running = new AtomicBoolean(false);

    public WebSocketServerTransport(
            String bindAddress,
            int port,
            String path,
            int maxMessageBytes,
            MessageRouter router,
            ConnectionRegistry registry,
            ConnectionLifecycleListener lifecycleListener,
            ServerSequence sequence) {
        super(new InetSocketAddress(bindAddress, port));
        this.path = normalizePath(path);
        this.codec = new JsonWireMessageCodec(maxMessageBytes);
        this.router = router;
        this.registry = registry;
        this.lifecycleListener = lifecycleListener;
        this.sequence = sequence;
        setReuseAddr(true);
        setConnectionLostTimeout(30);
    }

    public void startAndAwait() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("WebSocket server is already running");
        }
        start();
        try {
            if (!started.await(10, TimeUnit.SECONDS)) {
                throw new IOException("Timed out while starting WebSocket server");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while starting WebSocket server", exception);
        }
    }

    @Override
    public void onOpen(WebSocket socket, ClientHandshake handshake) {
        if (!path.equals(handshake.getResourceDescriptor())) {
            socket.close(1008, "Expected WebSocket path " + path);
            return;
        }
        WebSocketConnectionAdapter adapter = new WebSocketConnectionAdapter(
                socket, codec, registry, lifecycleListener);
        adapters.put(socket, adapter);
        registry.add(adapter);
        System.out.println("[WS] Connected " + adapter.getConnectionId()
                + " " + adapter.getRemoteAddress());
        sendWelcome(adapter);
    }

    @Override
    public void onMessage(WebSocket socket, String text) {
        WebSocketConnectionAdapter adapter = adapters.get(socket);
        if (adapter == null) {
            socket.close(1011, "Connection adapter missing");
            return;
        }
        adapter.markRead();
        try {
            router.route(adapter, codec.decode(text));
        } catch (IOException exception) {
            sendProtocolError(adapter, exception.getMessage());
        } catch (RuntimeException exception) {
            sendProtocolError(adapter, "Invalid WebSocket message");
        }
    }

    @Override
    public void onMessage(WebSocket socket, java.nio.ByteBuffer bytes) {
        WebSocketConnectionAdapter adapter = adapters.get(socket);
        if (adapter != null) {
            sendProtocolError(adapter, "Binary WebSocket frames are not supported");
        }
    }

    @Override
    public void onClose(WebSocket socket, int code, String reason, boolean remote) {
        WebSocketConnectionAdapter adapter = adapters.remove(socket);
        if (adapter != null) {
            adapter.close();
        }
    }

    @Override
    public void onError(WebSocket socket, Exception exception) {
        if (socket == null) {
            if (running.get()) {
                System.err.println("[WS] Server error: " + exception.getMessage());
            }
            return;
        }
        WebSocketConnectionAdapter adapter = adapters.get(socket);
        String id = adapter == null ? "unknown" : adapter.getConnectionId();
        System.err.println("[WS] Connection error " + id + ": " + exception.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("[WS] Listening on " + getAddress().getHostString()
                + ':' + getPort() + path);
        started.countDown();
    }

    public void awaitTermination() throws InterruptedException { terminated.await(); }
    public int getBoundPort() { return getPort(); }

    private void sendWelcome(ServerConnection connection) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("connectionId", connection.getConnectionId());
        data.put("serverName", "BTL16 Realtime Auction Server");
        data.put("protocolVersion", "1");
        data.put("transport", "WEBSOCKET");
        try {
            connection.send(WireMessage.event(
                    MessageType.CONNECTION_WELCOME,
                    sequence.next(),
                    data));
        } catch (IOException exception) {
            connection.close();
        }
    }

    private void sendProtocolError(ServerConnection connection, String message) {
        try {
            connection.send(WireMessage.event(
                    MessageType.ERROR,
                    sequence.next(),
                    Map.of(
                            "success", "false",
                            "errorCode", ErrorCode.INVALID_MESSAGE.name(),
                            "message", message == null ? "Invalid message" : message)));
        } catch (IOException exception) {
            connection.close();
        }
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        for (WebSocketConnectionAdapter adapter : adapters.values()) {
            adapter.close();
        }
        adapters.clear();
        try {
            stop(2000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            terminated.countDown();
            System.out.println("[WS] Server stopped");
        }
    }

    private static String normalizePath(String value) {
        String normalized = value == null || value.isBlank() ? "/ws" : value.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }
}
