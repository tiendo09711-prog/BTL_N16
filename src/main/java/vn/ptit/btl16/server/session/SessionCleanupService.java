package vn.ptit.btl16.server.session;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class SessionCleanupService implements AutoCloseable {
    private final SessionManager sessions;
    private final int intervalSeconds;
    private final ScheduledExecutorService scheduler;

    public SessionCleanupService(SessionManager sessions, int intervalSeconds) {
        this.sessions = sessions;
        this.intervalSeconds = intervalSeconds;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "session-cleanup");
            thread.setDaemon(true);
            return thread;
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(factory);
    }

    public void start() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                int removed = sessions.cleanupExpired();
                if (removed > 0) {
                    System.out.println("[SESSION] Removed expired sessions: " + removed);
                }
            } catch (RuntimeException exception) {
                System.err.println("[SESSION] Cleanup error: " + exception.getMessage());
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
