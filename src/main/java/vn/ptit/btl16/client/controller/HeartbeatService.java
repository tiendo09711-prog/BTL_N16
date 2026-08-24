package vn.ptit.btl16.client.controller;

import vn.ptit.btl16.client.network.NetworkClient;
import vn.ptit.btl16.client.service.AccountApi;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Periodically checks server liveness and measures round-trip time. */
public final class HeartbeatService implements AutoCloseable {
    private final NetworkClient network;
    private final AccountApi accounts;
    private final int intervalMillis;
    private final Consumer<String> latencyListener;
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    public HeartbeatService(
            NetworkClient network,
            AccountApi accounts,
            int intervalMillis,
            Consumer<String> latencyListener) {
        this.network = network;
        this.accounts = accounts;
        this.intervalMillis = intervalMillis;
        this.latencyListener = latencyListener;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "client-heartbeat");
            thread.setDaemon(true);
            return thread;
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(factory);
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::tick,
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS);
    }

    private void tick() {
        if (!network.isConnected()) {
            return;
        }
        accounts.ping().whenComplete((response, error) -> {
            if (error == null && response.isSuccess()) {
                consecutiveFailures.set(0);
                latencyListener.accept(response.get("roundTripMillis"));
                return;
            }
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= 3 && network.isConnected()) {
                network.disconnect();
                consecutiveFailures.set(0);
            }
        });
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
