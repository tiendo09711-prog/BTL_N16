package vn.ptit.btl16.server.network;

import vn.ptit.btl16.common.protocol.MessageCodec;
import vn.ptit.btl16.common.protocol.WireMessage;
import vn.ptit.btl16.server.routing.MessageRouter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClientConnection implements Runnable, AutoCloseable {
    private final String connectionId = UUID.randomUUID().toString();
    private final Socket socket;
    private final MessageCodec codec;
    private final MessageRouter router;
    private final ConnectionRegistry registry;
    private final ConnectionLifecycleListener lifecycleListener;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final Object outputLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile Instant lastReadAt = Instant.now();
    private volatile Instant lastWriteAt = Instant.now();

    public ClientConnection(
            Socket socket,
            MessageCodec codec,
            MessageRouter router,
            ConnectionRegistry registry,
            ConnectionLifecycleListener lifecycleListener) throws IOException {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.router = Objects.requireNonNull(router, "router");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.lifecycleListener = Objects.requireNonNull(lifecycleListener, "lifecycleListener");
        this.input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    @Override
    public void run() {
        System.out.println("[TCP] Connected " + connectionId + " " + getRemoteAddress());
        try {
            while (!closed.get()) {
                WireMessage message = codec.read(input);
                lastReadAt = Instant.now();
                router.route(this, message);
            }
        } catch (EOFException | SocketException normalDisconnect) {
            // Normal disconnect path.
        } catch (IOException exception) {
            if (!closed.get()) {
                System.err.println("[TCP] Connection error " + connectionId + ": " + exception.getMessage());
            }
        } catch (RuntimeException exception) {
            if (!closed.get()) {
                System.err.println("[TCP] Runtime error " + connectionId + ": " + exception.getMessage());
            }
        } finally {
            close();
        }
    }

    public void send(WireMessage message) throws IOException {
        if (closed.get()) {
            throw new SocketException("Connection is closed");
        }
        synchronized (outputLock) {
            if (closed.get()) {
                throw new SocketException("Connection is closed");
            }
            codec.write(output, message);
            lastWriteAt = Instant.now();
        }
    }

    public String getConnectionId() { return connectionId; }
    public String getRemoteAddress() { return String.valueOf(socket.getRemoteSocketAddress()); }
    public boolean isClosed() { return closed.get(); }
    public Instant getLastReadAt() { return lastReadAt; }
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
            System.err.println("[TCP] Disconnect listener error: " + exception.getMessage());
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // Best effort.
        }
        System.out.println("[TCP] Disconnected " + connectionId);
    }
}
