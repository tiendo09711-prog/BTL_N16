package vn.ptit.btl16.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

public final class Money {
    private static final DecimalFormat DISPLAY = new DecimalFormat("#,##0");

    private Money() {
    }

    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Money value is null");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static String wire(BigDecimal value) {
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
