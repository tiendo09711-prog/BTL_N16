package vn.ptit.btl16.server.network;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.MessageCodec;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.WireMessage;
import vn.ptit.btl16.server.ServerSequence;
import vn.ptit.btl16.server.routing.MessageRouter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class TcpServer implements AutoCloseable {
    private final ServerConfig config;
    private final MessageCodec codec;
    private final MessageRouter router;
    private final ConnectionRegistry registry;
    private final ConnectionLifecycleListener lifecycleListener;
    private final ServerSequence sequence;
    private final ExecutorService workers;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CountDownLatch terminated = new CountDownLatch(1);

    private volatile ServerSocket serverSocket;
    private volatile Thread acceptThread;
    private volatile int boundPort;

    public TcpServer(
            ServerConfig config,
            MessageCodec codec,
            MessageRouter router,
            ConnectionRegistry registry,
            ConnectionLifecycleListener lifecycleListener,
            ServerSequence sequence) {
        this.config = config;
        this.codec = codec;
        this.router = router;
        this.registry = registry;
        this.lifecycleListener = lifecycleListener;
        this.sequence = sequence;

        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "client-worker-" + counter.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
        this.workers = Executors.newFixedThreadPool(config.getWorkerThreads(), factory);
    }

    public synchronized void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Server is already running");
        }
        try {
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(
                    new InetSocketAddress(config.getBindAddress(), config.getPort()),
                    config.getBacklog());
            this.serverSocket = socket;
            this.boundPort = socket.getLocalPort();
            this.acceptThread = new Thread(this::acceptLoop, "tcp-acceptor");
            this.acceptThread.setDaemon(false);
            this.acceptThread.start();
            System.out.println("[TCP] Listening on " + config.getBindAddress() + ':' + boundPort);
        } catch (IOException exception) {
            running.set(false);
            terminated.countDown();
            throw exception;
        }
    }

    private void acceptLoop() {
        try {
            while (running.get()) {
                Socket socket = serverSocket.accept();
                configure(socket);
                ClientConnection connection = new ClientConnection(
                        socket,
                        codec,
                        router,
                        registry,
                        lifecycleListener);
                registry.add(connection);
                sendWelcome(connection);
                workers.execute(connection);
            }
        } catch (SocketException closedSocket) {
            if (running.get()) {
                System.err.println("[TCP] Acceptor closed unexpectedly: " + closedSocket.getMessage());
            }
        } catch (IOException exception) {
            if (running.get()) {
                System.err.println("[TCP] Acceptor error: " + exception.getMessage());
            }
        } finally {
            running.set(false);
            terminated.countDown();
        }
    }

    private void configure(Socket socket) throws SocketException {
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
    }

    private void sendWelcome(ClientConnection connection) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("connectionId", connection.getConnectionId());
        data.put("serverName", "BTL16 Realtime Auction Server");
        data.put("protocolVersion", "1");
        try {
            connection.send(WireMessage.event(
                    MessageType.CONNECTION_WELCOME,
                    sequence.next(),
                    data));
        } catch (IOException exception) {
            connection.close();
        }
    }

    public void awaitTermination() throws InterruptedException {
        terminated.await();
    }

    public int getBoundPort() { return boundPort; }
    public boolean isRunning() { return running.get(); }

    @Override
    public synchronized void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // Best effort.
        }
        registry.closeAll();
        workers.shutdownNow();
        terminated.countDown();
        System.out.println("[TCP] Server stopped");
    }
}
