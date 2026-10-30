package vn.ptit.btl16.common.util;

import java.time.Instant;
import java.time.ZoneId;

public enum RevenuePeriod {
    ONE_YEAR("1y", "1 n\u0103m", 12),
    SIX_MONTHS("6m", "6 th\u00e1ng", 6),
    THREE_MONTHS("3m", "3 th\u00e1ng", 3),
    ONE_MONTH("1m", "1 th\u00e1ng", 1),
    SEVEN_DAYS("7d", "7 ng\u00e0y", 0);

    public static final RevenuePeriod DEFAULT = ONE_MONTH;
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final String code;
    private final String label;
    private final int months;

    RevenuePeriod(String code, String label, int months) {
        this.code = code; this.label = label; this.months = months;
    }

    public String code() { return code; }
    public Instant startAt(Instant end) {
        var local = end.atZone(ZONE);
        return (months == 0 ? local.minusDays(7) : local.minusMonths(months)).toInstant();
    }
    public static RevenuePeriod parse(String code) {
        if (code == null || code.isBlank()) { return DEFAULT; }
        for (var period : values()) { if (period.code.equals(code)) { return period; } }
        throw new IllegalArgumentException("Invalid revenue period");
    }
    @Override public String toString() { return label; }
}
