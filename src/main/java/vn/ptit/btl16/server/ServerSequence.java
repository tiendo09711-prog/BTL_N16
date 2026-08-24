package vn.ptit.btl16.server;

import java.util.concurrent.atomic.AtomicLong;

public final class ServerSequence {
    private final AtomicLong value;

    public ServerSequence() {
        this(0L);
    }

    public ServerSequence(long initialValue) {
        this.value = new AtomicLong(Math.max(0L, initialValue));
    }

    public long next() {
        return value.incrementAndGet();
    }

    public long current() {
        return value.get();
    }
}
