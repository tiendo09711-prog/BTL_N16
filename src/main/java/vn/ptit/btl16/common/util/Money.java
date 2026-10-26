package vn.ptit.btl16.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

public final class Money {
    private static final DecimalFormat DISPLAY = new DecimalFormat("#,##0.##");

    private Money() {
    }

    public static BigDecimal parseInput(String text) {
        String raw = text == null ? "" : text.trim();
        if (raw.length() > 64) { throw new IllegalArgumentException("Invalid money input"); }
        if (raw.matches("[1-9][0-9]{0,2}(?:\\.[0-9]{3})+(?:,[0-9]{1,2})?")) {
            raw = raw.replace(".", "").replace(',', '.');
        } else if (raw.matches("[0-9]+(?:[,.][0-9]{1,2})?")) {
            raw = raw.replace(',', '.');
        } else { throw new IllegalArgumentException("Invalid money input"); }
        BigDecimal amount = new BigDecimal(raw);
        if (amount.precision() > 32 || amount.signum() <= 0) {
            throw new IllegalArgumentException("Invalid money input");
        }
        return normalize(amount);
    }

    public static String input(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        format.setMaximumFractionDigits(2);
        format.setRoundingMode(RoundingMode.UNNECESSARY);
        return format.format(normalize(value));
    }

    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Money value is null");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static String wire(BigDecimal value) {
        if (value != null && (value.precision() > 32 || Math.abs((long) value.scale()) > 32)) {
            throw new IllegalArgumentException("Money value has excessive precision or scale");
        }
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    public static String display(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        synchronized (DISPLAY) {
            return DISPLAY.format(value) + " VND";
        }
    }
}
