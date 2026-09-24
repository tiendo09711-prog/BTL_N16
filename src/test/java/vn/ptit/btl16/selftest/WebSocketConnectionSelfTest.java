package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.network.ConnectionState;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.AccountApi;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class WebSocketConnectionSelfTest {
    private WebSocketConnectionSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Supply a running server's ws://host:port/ws endpoint");
        }
        run(args[0]);
    }

    public static void run(String healthyEndpoint) throws Exception {
        cancelAndChangeEndpoint(healthyEndpoint);
        closeDuringHandshake();
        handshakeTimeout();
        reconnect(healthyEndpoint);
        System.out.println("[PASS] WebSocketConnectionSelfTest");
    }

    private static void cancelAndChangeEndpoint(String healthyEndpoint) throws Exception {
        try (ServerSocket silentServer = silentServer();
             WebSocketClientTransport transport = transport(endpoint(silentServer), 3000)) {
            CompletableFuture<Void> firstAttempt = transport.connect();
            try (Socket ignored = silentServer.accept()) {
                TestSupport.check(firstAttempt == transport.connect(),
                        "Concurrent connect calls reuse the active attempt");
                transport.disconnect();
                TestSupport.check(firstAttempt.isCompletedExceptionally(),
                        "Disconnect immediately completes the old connection attempt");
                TestSupport.equals(ConnectionState.DISCONNECTED, transport.getState(),
                        "Disconnect clears the connecting state");
                transport.setEndpoint(healthyEndpoint);
                CompletableFuture<Void> nextAttempt = transport.connect();
                TestSupport.check(firstAttempt != nextAttempt,
                        "Changing endpoint starts a new connection, not the old pending attempt");
                nextAttempt.get(5, TimeUnit.SECONDS);
                TestSupport.check(new AccountApi(transport).ping().get(5, TimeUnit.SECONDS).isSuccess(),
                        "The new endpoint responds after cancelling a stalled handshake");
            }
            TestSupport.check(new AccountApi(transport).ping().get(5, TimeUnit.SECONDS).isSuccess(),
                    "Closing the old endpoint does not disconnect the new socket");
        }
    }

    private static void closeDuringHandshake() throws Exception {
        try (ServerSocket silentServer = silentServer();
             WebSocketClientTransport transport = transport(endpoint(silentServer), 3000)) {
            AtomicInteger disconnectedEvents = new AtomicInteger();
            transport.addStateListener((state, detail) -> {
                if (state == ConnectionState.DISCONNECTED) {
                    disconnectedEvents.incrementAndGet();
                }
            });
            CompletableFuture<Void> attempt = transport.connect();
            try (Socket ignored = silentServer.accept()) {
                transport.close();
                TestSupport.check(attempt.isCompletedExceptionally(),
                        "Close immediately completes an in-flight handshake");
                TestSupport.equals(ConnectionState.CLOSED, transport.getState(),
                        "Transport stays closed after handshake cancellation");
                TestSupport.check(transport.connect().isCompletedExceptionally(),
                        "A closed transport cannot reconnect");
                TestSupport.equals(0, disconnectedEvents.get(),
                        "Cancelled handshake does not emit a stale disconnected event");
            }
        }
    }

    private static void handshakeTimeout() throws Exception {
        try (ServerSocket silentServer = silentServer();
             WebSocketClientTransport transport = transport(endpoint(silentServer), 500)) {
            CompletableFuture<Void> attempt = transport.connect();
            try (Socket ignored = silentServer.accept()) {
                try {
                    attempt.get(3, TimeUnit.SECONDS);
                    throw new AssertionError("An endpoint without a WebSocket handshake must fail");
                } catch (ExecutionException expected) {
                    TestSupport.equals(ConnectionState.DISCONNECTED, transport.getState(),
                            "Handshake timeout clears the connecting state");
                }
            }
        }
    }

    private static void reconnect(String endpoint) throws Exception {
        try (WebSocketClientTransport transport = transport(endpoint, 3000)) {
            for (int attempt = 0; attempt < 5; attempt++) {
                long started = System.nanoTime();
                transport.connect().get(5, TimeUnit.SECONDS);
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                TestSupport.check(new AccountApi(transport).ping().get(5, TimeUnit.SECONDS).isSuccess(),
                        "Reconnected socket answers ping");
                System.out.println("[WS CHECK] " + endpoint + " handshake=" + elapsedMillis + " ms");
                transport.disconnect();
            }
        }
    }

    private static ServerSocket silentServer() throws Exception {
        ServerSocket server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
        server.setSoTimeout(5000);
        return server;
    }

    private static String endpoint(ServerSocket server) {
        return "ws://127.0.0.1:" + server.getLocalPort() + "/ws";
    }

    private static WebSocketClientTransport transport(String endpoint, int timeoutMillis) {
        return new WebSocketClientTransport(endpoint, 2_097_152, timeoutMillis, 3000);
    }
}
