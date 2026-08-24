package vn.ptit.btl16.selftest;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

public final class TestSupport {
    private TestSupport() {
    }

    public static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void equals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " | expected=" + expected + ", actual=" + actual);
        }
    }

    public static void waitUntil(BooleanSupplier condition, long timeoutMillis, String message)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofMillis(timeoutMillis));
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25L);
        }
        throw new AssertionError("Timeout: " + message);
    }
}
