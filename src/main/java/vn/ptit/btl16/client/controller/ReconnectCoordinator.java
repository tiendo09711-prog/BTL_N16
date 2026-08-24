package vn.ptit.btl16.client.controller;

import vn.ptit.btl16.client.network.NetworkClient;
import vn.ptit.btl16.common.config.ClientConfig;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exponential-backoff TCP reconnection. Session resume is handled by ClientController. */
public final class ReconnectCoordinator implements AutoCloseable {
    public interface Listener {
        void onAttempt(int attempt, int maxAttempts, long delayMillis);
        void onTransportConnected();
        void onExhausted(String lastError);
    }

    private final NetworkClient network;
    private final ClientConfig config;
    private final Listener listener;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile String lastError = "";

    public ReconnectCoordinator(
            NetworkClient network,
            ClientConfig config,
            Listener listener) {
        this.network = network;
        this.config = config;
        this.listener = listener;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "client-reconnect");
            thread.setDaemon(true);
            return thread;
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(factory);
    }

    public void start() {
        if (closed.get() || network.isConnected() || !running.compareAndSet(false, true)) {
            return;
        }
        scheduleAttempt(1, config.getReconnectInitialDelayMillis());
    }

    public void cancel() {
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    private void scheduleAttempt(int attempt, long delayMillis) {
        if (!running.get() || closed.get()) {
            return;
        }
        listener.onAttempt(attempt, config.getReconnectMaxAttempts(), delayMillis);
        scheduler.schedule(
                () -> attemptConnect(attempt),
                delayMillis,
                TimeUnit.MILLISECONDS);
    }

    private void attemptConnect(int attempt) {
        if (!running.get() || closed.get()) {
            return;
        }
        try {
            network.connect(config.getServerHost(), config.getServerPort());
            running.set(false);
            listener.onTransportConnected();
        } catch (Exception exception) {
            lastError = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            if (attempt >= config.getReconnectMaxAttempts()) {
                running.set(false);
                listener.onExhausted(lastError);
                return;
            }
            long nextDelay = Math.min(
                    config.getReconnectMaxDelayMillis(),
                    Math.max(config.getReconnectInitialDelayMillis(),
                            config.getReconnectInitialDelayMillis() * (1L << Math.min(10, attempt))));
            scheduleAttempt(attempt + 1, nextDelay);
        }
    }

    @Override
    public void close() {
        closed.set(true);
        running.set(false);
        scheduler.shutdownNow();
    }
}
