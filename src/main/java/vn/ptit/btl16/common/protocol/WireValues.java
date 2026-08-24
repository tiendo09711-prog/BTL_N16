package vn.ptit.btl16.common.protocol;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public final class WireValues {
    private WireValues() {
    }

    public static long requireLong(Map<String, String> data, String key) {
        String raw = require(data, key);
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a long: " + raw, exception);
        }
    }

    public static int requireInt(Map<String, String> data, String key) {
        String raw = require(data, key);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer: " + raw, exception);
        }
    }

    public static BigDecimal requireDecimal(Map<String, String> data, String key) {
        String raw = require(data, key).replace(",", "").trim();
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a decimal: " + raw, exception);
        }
    }

    public static Instant optionalInstant(Map<String, String> data, String key) {
        String raw = data.getOrDefault(key, "");
        return raw.isBlank() ? null : Instant.parse(raw);
    }

    public static String require(Map<String, String> data, String key) {
        String value = data.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return value;
    }

    public static String text(Map<String, String> data, String key) {
        return data.getOrDefault(key, "");
    }
}
