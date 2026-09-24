package vn.ptit.btl16.client.network;

import vn.ptit.btl16.common.protocol.JsonWireMessageCodec;
import vn.ptit.btl16.common.protocol.MessageKind;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.WireMessage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class WebSocketClientTransport implements ClientTransport {
    private final JsonWireMessageCodec codec;
    private final int connectTimeoutMillis;
    private final int requestTimeoutMillis;
    private final HttpClient httpClient;
    private final ConcurrentHashMap<String, CompletableFuture<WireMessage>> pendingRequests =
            new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<WireMessage>> eventListeners =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ConnectionStateListener> stateListeners =
            new CopyOnWriteArrayList<>();
    private final AtomicReference<ConnectionState> state =
            new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final ScheduledExecutorService timeoutScheduler;
    private final StringBuilder textBuffer = new StringBuilder();

    private volatile URI endpoint;
    private volatile WebSocket socket;
    private volatile CompletableFuture<Void> connecting;
    private CompletableFuture<WebSocket> handshake;

    public WebSocketClientTransport(
            String endpoint,
            int maxMessageBytes,
            int connectTimeoutMillis,
            int requestTimeoutMillis) {
        this.codec = new JsonWireMessageCodec(maxMessageBytes);
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.requestTimeoutMillis = requestTimeoutMillis;
        this.endpoint = parseEndpoint(endpoint);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                .build();
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ws-client-timeouts");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public synchronized CompletableFuture<Void> connect() {
        if (state.get() == ConnectionState.CLOSED) {
            return CompletableFuture.failedFuture(new IllegalStateException("Transport is closed"));
        }
        if (state.get() == ConnectionState.CONNECTED) {
            return CompletableFuture.completedFuture(null);
        }
        if (connecting != null && !connecting.isDone()) {
            return connecting;
        }
        changeState(ConnectionState.CONNECTING, endpoint.toString());
        CompletableFuture<Void> result = new CompletableFuture<>();
        connecting = result;
        try {
            handshake = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                    .buildAsync(endpoint, new ConnectionListener(result));
            handshake.whenComplete((value, error) -> {
                synchronized (WebSocketClientTransport.this) {
                    if (connecting == result && !result.isDone() && error != null) {
                        changeState(ConnectionState.DISCONNECTED, rootMessage(error));
                        result.completeExceptionally(error);
                    }
                }
            });
        } catch (RuntimeException exception) {
            changeState(ConnectionState.DISCONNECTED, rootMessage(exception));
            result.completeExceptionally(exception);
        }
        return result;
    }

    private final class ConnectionListener implements WebSocket.Listener {
        private final CompletableFuture<Void> attempt;

        private ConnectionListener(CompletableFuture<Void> attempt) {
            this.attempt = attempt;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            synchronized (WebSocketClientTransport.this) {
                if (connecting != attempt || attempt.isDone()
                        || state.get() != ConnectionState.CONNECTING) {
                    webSocket.abort();
                    return;
                }
                socket = webSocket;
                changeState(ConnectionState.CONNECTED, endpoint.toString());
                attempt.complete(null);
                webSocket.request(1);
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            synchronized (WebSocketClientTransport.this) {
                if (socket != webSocket) {
                    return CompletableFuture.completedFuture(null);
                }
                return handleText(webSocket, data, last);
            }
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            synchronized (WebSocketClientTransport.this) {
                if (socket == webSocket) {
                    disconnectWithError("Server sent unsupported binary WebSocket frame");
                }
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            synchronized (WebSocketClientTransport.this) {
                if (socket == webSocket) {
                    disconnectWithError("WebSocket closed: " + statusCode + " " + reason);
                }
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            synchronized (WebSocketClientTransport.this) {
                if (socket == webSocket) {
                    disconnectWithError("WebSocket error: " + rootMessage(error));
                }
            }
        }
    }

    private CompletionStage<?> handleText(WebSocket webSocket, CharSequence data, boolean last) {
        String complete = null;
        textBuffer.append(data);
        if (last) {
            complete = textBuffer.toString();
            textBuffer.setLength(0);
        }
        if (complete != null) {
            try {
                dispatch(codec.decode(complete));
            } catch (IOException exception) {
                disconnectWithError("Invalid server message: " + exception.getMessage());
            }
        }
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<WireMessage> sendRequest(
            MessageType type,
            Map<String, String> data) {
        WebSocket current = socket;
        if (current == null || state.get() != ConnectionState.CONNECTED) {
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
            current.sendText(codec.encode(request), true).whenComplete((value, error) -> {
                if (error != null) {
                    CompletableFuture<WireMessage> pending = pendingRequests.remove(requestId);
                    if (pending != null) {
                        pending.completeExceptionally(error);
                    }
                }
            });
        } catch (IOException exception) {
            pendingRequests.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    @Override
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

    private void dispatch(WireMessage message) {
        if (message.getKind() == MessageKind.RESPONSE && !message.getRequestId().isBlank()) {
            CompletableFuture<WireMessage> pending = pendingRequests.remove(message.getRequestId());
            if (pending != null) {
                pending.complete(message);
                return;
            }
        }
        for (Consumer<WireMessage> listener : eventListeners) {
            try {
                listener.accept(message);
            } catch (RuntimeException exception) {
                System.err.println("[WS CLIENT] Event listener error: " + exception.getMessage());
            }
        }
    }

    @Override
    public synchronized void disconnect() {
        cancelConnecting("Client disconnected");
        WebSocket current = socket;
        socket = null;
        textBuffer.setLength(0);
        if (current != null) {
            current.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnected");
        }
        failAllPending(new IOException("Client disconnected"));
        if (state.get() != ConnectionState.CLOSED) {
            changeState(ConnectionState.DISCONNECTED, "Client disconnected");
        }
    }

    @Override
    public boolean isConnected() { return state.get() == ConnectionState.CONNECTED; }
    @Override
    public ConnectionState getState() { return state.get(); }
    @Override
    public String getEndpoint() { return endpoint.toString(); }

    @Override
    public synchronized void setEndpoint(String endpoint) {
        if (isConnected() || state.get() == ConnectionState.CONNECTING) {
            throw new IllegalStateException("Disconnect before changing WebSocket endpoint");
        }
        this.endpoint = parseEndpoint(endpoint);
    }

    @Override
    public void addEventListener(Consumer<WireMessage> listener) {
        eventListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void addStateListener(ConnectionStateListener listener) {
        stateListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    private void changeState(ConnectionState newState, String detail) {
        state.set(newState);
        for (ConnectionStateListener listener : stateListeners) {
            try {
                listener.onStateChanged(newState, detail == null ? "" : detail);
            } catch (RuntimeException exception) {
                System.err.println("[WS CLIENT] State listener error: " + exception.getMessage());
            }
        }
    }

    private void disconnectWithError(String detail) {
        WebSocket current = socket;
        socket = null;
        textBuffer.setLength(0);
        if (current != null) {
            current.abort();
        }
        failAllPending(new IOException(detail));
        if (state.get() != ConnectionState.CLOSED) {
            changeState(ConnectionState.DISCONNECTED, detail);
        }
    }

    private void failAllPending(Exception exception) {
        for (Map.Entry<String, CompletableFuture<WireMessage>> entry : pendingRequests.entrySet()) {
            if (pendingRequests.remove(entry.getKey(), entry.getValue())) {
                entry.getValue().completeExceptionally(exception);
            }
        }
    }

    @Override
    public synchronized void close() {
        if (state.get() == ConnectionState.CLOSED) {
            return;
        }
        cancelConnecting("WebSocket transport closed");
        WebSocket current = socket;
        socket = null;
        textBuffer.setLength(0);
        if (current != null) {
            current.sendClose(WebSocket.NORMAL_CLOSURE, "Application closed");
        }
        failAllPending(new IOException("WebSocket transport closed"));
        timeoutScheduler.shutdownNow();
        changeState(ConnectionState.CLOSED, "Application closed");
    }

    private void cancelConnecting(String reason) {
        CompletableFuture<Void> attempt = connecting;
        CompletableFuture<WebSocket> pendingHandshake = handshake;
        connecting = null;
        handshake = null;
        if (attempt != null && !attempt.isDone()) {
            attempt.completeExceptionally(new IOException(reason));
        }
        if (pendingHandshake != null && !pendingHandshake.isDone()) {
            pendingHandshake.cancel(true);
        }
    }

    private URI parseEndpoint(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            String scheme = uri.getScheme();
            if ((scheme == null || (!scheme.equalsIgnoreCase("ws")
                    && !scheme.equalsIgnoreCase("wss"))) || uri.getHost() == null) {
                throw new IllegalArgumentException("WebSocket endpoint must use ws:// or wss://");
            }
            return uri;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid WebSocket endpoint: " + value, exception);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
