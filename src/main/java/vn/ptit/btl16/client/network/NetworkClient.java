package vn.ptit.btl16.client.network;

import vn.ptit.btl16.common.protocol.LengthPrefixedMessageCodec;
import vn.ptit.btl16.common.protocol.MessageCodec;
import vn.ptit.btl16.common.protocol.MessageKind;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.WireMessage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Client-side TCP core. One reader thread receives both responses and realtime events.
 * Responses are paired with requests through requestId.
 */
public final class NetworkClient implements AutoCloseable {
    private final MessageCodec codec;
    private final int connectTimeoutMillis;
    private final int requestTimeoutMillis;
    private final Object lifecycleLock = new Object();
    private final Object outputLock = new Object();
    private final ConcurrentHashMap<String, CompletableFuture<WireMessage>> pendingRequests =
            new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<WireMessage>> eventListeners =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ConnectionStateListener> stateListeners =
            new CopyOnWriteArrayList<>();
    private final AtomicReference<ConnectionState> state =
            new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final ScheduledExecutorService timeoutScheduler;

    private volatile Socket socket;
    private volatile DataInputStream input;
    private volatile DataOutputStream output;
    private volatile Thread readerThread;

    public NetworkClient(int maxFrameBytes, int connectTimeoutMillis, int requestTimeoutMillis) {
        this.codec = new LengthPrefixedMessageCodec(maxFrameBytes);
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.requestTimeoutMillis = requestTimeoutMillis;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "client-request-timeouts");
            thread.setDaemon(true);
            return thread;
        };
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(factory);
    }

    public void connect(String host, int port) throws IOException {
        Objects.requireNonNull(host, "host");
        synchronized (lifecycleLock) {
            ConnectionState current = state.get();
            if (current == ConnectionState.CLOSED) {
                throw new IllegalStateException("NetworkClient is closed");
            }
            if (current == ConnectionState.CONNECTED || current == ConnectionState.CONNECTING) {
                return;
            }

            changeState(ConnectionState.CONNECTING, host + ':' + port);
            Socket newSocket = new Socket();
            try {
                newSocket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
                newSocket.setTcpNoDelay(true);
                newSocket.setKeepAlive(true);
                DataInputStream newInput = new DataInputStream(
                        new BufferedInputStream(newSocket.getInputStream()));
                DataOutputStream newOutput = new DataOutputStream(
                        new BufferedOutputStream(newSocket.getOutputStream()));

                socket = newSocket;
                input = newInput;
                output = newOutput;
                changeState(
                        ConnectionState.CONNECTED,
                        String.valueOf(newSocket.getRemoteSocketAddress()));

                Thread thread = new Thread(
                        () -> readLoop(newSocket, newInput),
                        "server-reader");
                thread.setDaemon(true);
                readerThread = thread;
                thread.start();
            } catch (IOException exception) {
                closeQuietly(newSocket);
                socket = null;
                input = null;
                output = null;
                changeState(ConnectionState.DISCONNECTED, exception.getMessage());
                throw exception;
            }
        }
    }

    public CompletableFuture<WireMessage> sendRequest(
            MessageType type,
            Map<String, String> data) {
        if (state.get() != ConnectionState.CONNECTED) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Client is not connected"));
        }

        String requestId = UUID.randomUUID().toString();
        WireMessage request = WireMessage.request(type, requestId, data);
        CompletableFuture<WireMessage> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        ScheduledFuture<?> timeout = timeoutScheduler.schedule(() -> {
            CompletableFuture<WireMessage> pending = pendingRequests.remove(requestId);
            if (pending != null) {
                pending.completeExceptionally(new TimeoutException(
                        "Request " + type + " timed out after " + requestTimeoutMillis + " ms"));
            }
        }, requestTimeoutMillis, TimeUnit.MILLISECONDS);
        future.whenComplete((value, error) -> timeout.cancel(false));

        try {
            send(request);
        } catch (IOException exception) {
            pendingRequests.remove(requestId);
            future.completeExceptionally(exception);
            disconnectOwned(socket, "Send failed: " + exception.getMessage());
        }
        return future;
    }

    public CompletableFuture<WireMessage> ping() {
        long started = System.nanoTime();
        return sendRequest(MessageType.PING, Map.of()).thenApply(response -> {
            Map<String, String> data = new LinkedHashMap<>(response.getData());
            data.put("roundTripMillis", Long.toString(
                    Duration.ofNanos(System.nanoTime() - started).toMillis()));
            return new WireMessage(
                    response.getVersion(),
                    response.getKind(),
                    response.getType(),
                    response.getRequestId(),
                    response.getServerSequence(),
                    response.getSentAtEpochMillis(),
                    data);
        });
    }

    private void send(WireMessage message) throws IOException {
        DataOutputStream currentOutput = output;
        if (currentOutput == null || state.get() != ConnectionState.CONNECTED) {
            throw new SocketException("No active connection");
        }
        synchronized (outputLock) {
            if (currentOutput != output || state.get() != ConnectionState.CONNECTED) {
                throw new SocketException("Connection changed before write");
            }
            codec.write(currentOutput, message);
        }
    }

    private void readLoop(Socket ownedSocket, DataInputStream ownedInput) {
        try {
            while (!ownedSocket.isClosed()) {
                WireMessage message = codec.read(ownedInput);
                if (message.getKind() == MessageKind.RESPONSE
                        && !message.getRequestId().isBlank()) {
                    CompletableFuture<WireMessage> pending =
                            pendingRequests.remove(message.getRequestId());
                    if (pending != null) {
                        pending.complete(message);
                        continue;
                    }
                }
                for (Consumer<WireMessage> listener : eventListeners) {
                    try {
                        listener.accept(message);
                    } catch (RuntimeException exception) {
                        System.err.println("[CLIENT] Event listener error: " + exception.getMessage());
                    }
                }
            }
        } catch (EOFException | SocketException normalDisconnect) {
            disconnectOwned(ownedSocket, "Server closed the connection");
        } catch (IOException exception) {
            disconnectOwned(ownedSocket, exception.getMessage());
        } catch (RuntimeException exception) {
            disconnectOwned(ownedSocket, "Reader error: " + exception.getMessage());
        }
    }

    public void disconnect() {
        disconnectOwned(socket, "Client disconnected");
    }

    private void disconnectOwned(Socket ownedSocket, String detail) {
        synchronized (lifecycleLock) {
            if (ownedSocket == null || socket != ownedSocket) {
                return;
            }
            closeQuietly(ownedSocket);
            socket = null;
            input = null;
            output = null;
            readerThread = null;
            failAllPending(new IOException("Connection lost: " + detail));
            if (state.get() != ConnectionState.CLOSED) {
                changeState(ConnectionState.DISCONNECTED, detail);
            }
        }
    }

    private void failAllPending(Exception exception) {
        for (Map.Entry<String, CompletableFuture<WireMessage>> entry : pendingRequests.entrySet()) {
            if (pendingRequests.remove(entry.getKey(), entry.getValue())) {
                entry.getValue().completeExceptionally(exception);
            }
        }
    }

    public void addEventListener(Consumer<WireMessage> listener) {
        eventListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void addStateListener(ConnectionStateListener listener) {
        stateListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public ConnectionState getState() {
        return state.get();
    }

    public boolean isConnected() {
        return state.get() == ConnectionState.CONNECTED;
    }

    private void changeState(ConnectionState newState, String detail) {
        state.set(newState);
        for (ConnectionStateListener listener : stateListeners) {
            try {
                listener.onStateChanged(newState, detail == null ? "" : detail);
            } catch (RuntimeException exception) {
                System.err.println("[CLIENT] State listener error: " + exception.getMessage());
            }
        }
    }

    private void closeQuietly(Socket value) {
        if (value == null) {
            return;
        }
        try {
            value.close();
        } catch (IOException ignored) {
            // Best effort.
        }
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (state.get() == ConnectionState.CLOSED) {
                return;
            }
            Socket current = socket;
            if (current != null) {
                closeQuietly(current);
            }
            socket = null;
            input = null;
            output = null;
            readerThread = null;
            failAllPending(new IOException("NetworkClient closed"));
            timeoutScheduler.shutdownNow();
            changeState(ConnectionState.CLOSED, "Application closed");
        }
    }
}
