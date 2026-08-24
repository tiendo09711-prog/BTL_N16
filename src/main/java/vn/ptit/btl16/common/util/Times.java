package vn.ptit.btl16.common.util;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class Times {
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private Times() {
    }

    public static String wire(Instant value) {
        return value == null ? "" : value.toString();
    }

    public static String display(Instant value) {
        return value == null ? "-" : DISPLAY.format(value);
    }

    public static String countdown(Instant endTime, Instant now) {
        if (endTime == null) {
            return "--:--";
        }
        long seconds = Math.max(0L, Duration.between(now, endTime).getSeconds());
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remain = seconds % 60;
        return hours > 0
                ? String.format("%02d:%02d:%02d", hours, minutes, remain)
                : String.format("%02d:%02d", minutes, remain);
    }
}
