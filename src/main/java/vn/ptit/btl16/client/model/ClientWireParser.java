package vn.ptit.btl16.client.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts the flat wire map into typed client models. */
public final class ClientWireParser {
    private ClientWireParser() {
    }

    public static List<ClientAuction> auctions(Map<String, String> data) {
        int count = integer(data, "count", 0);
        List<ClientAuction> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(auction(data, "auction." + i + '.'));
        }
        return values;
    }

    public static ClientAuction auction(Map<String, String> data, String prefix) {
        return new ClientAuction(
                longValue(data, prefix + "auctionId", 0L),
                longValue(data, prefix + "productId", 0L),
                text(data, prefix + "productCode"),
                text(data, prefix + "productName"),
                text(data, prefix + "description"),
                longValue(data, prefix + "hostUserId", 0L),
                text(data, prefix + "hostUsername"),
                decimal(data, prefix + "startPrice"),
                decimal(data, prefix + "minBidIncrement"),
                decimal(data, prefix + "currentPrice"),
                nullableLong(data, prefix + "currentWinnerId"),
                text(data, prefix + "currentWinnerUsername"),
                instant(data, prefix + "startTime"),
                instant(data, prefix + "endTime"),
                text(data, prefix + "status"),
                instant(data, prefix + "endedAt"),
                longValue(data, prefix + "version", 0L),
                integer(data, prefix + "watcherCount", 0));
    }

    public static List<ClientProduct> products(Map<String, String> data) {
        int count = integer(data, "productCount", 0);
        List<ClientProduct> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(product(data, "product." + i + '.'));
        }
        return values;
    }

    public static ClientProduct product(Map<String, String> data, String prefix) {
        return new ClientProduct(
                longValue(data, prefix + "productId", 0L),
                longValue(data, prefix + "ownerId", 0L),
                text(data, prefix + "ownerUsername"),
                text(data, prefix + "code"),
                text(data, prefix + "name"),
                text(data, prefix + "description"),
                Boolean.parseBoolean(text(data, prefix + "active")),
                instant(data, prefix + "createdAt"),
                instant(data, prefix + "updatedAt"));
    }

    public static List<ClientBid> bids(Map<String, String> data) {
        int count = integer(data, "bidCount", 0);
        List<ClientBid> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(bid(data, "bid." + i + '.'));
        }
        return values;
    }

    public static ClientBid bid(Map<String, String> data, String prefix) {
        return new ClientBid(
                longValue(data, prefix + "bidId", 0L),
                longValue(data, prefix + "auctionId", 0L),
                longValue(data, prefix + "userId", 0L),
                text(data, prefix + "username"),
                decimal(data, prefix + "amount"),
                longValue(data, prefix + "serverSequence", 0L),
                instant(data, prefix + "createdAt"));
    }

    public static Instant serverNow(Map<String, String> data) {
        return instant(data, "serverNow");
    }

    public static Instant instant(Map<String, String> data, String key) {
        String raw = text(data, key);
        if (raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static long longValue(Map<String, String> data, String key, long fallback) {
        try {
            String raw = text(data, key);
            return raw.isBlank() ? fallback : Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public static int integer(Map<String, String> data, String key, int fallback) {
        try {
            String raw = text(data, key);
            return raw.isBlank() ? fallback : Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public static BigDecimal decimal(Map<String, String> data, String key) {
        try {
            String raw = text(data, key);
            return raw.isBlank() ? BigDecimal.ZERO : new BigDecimal(raw);
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    public static String text(Map<String, String> data, String key) {
        return data.getOrDefault(key, "");
    }

    private static Long nullableLong(Map<String, String> data, String key) {
        String raw = text(data, key);
        if (raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
