package vn.ptit.btl16.server.auction.repository;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.service.AuctionException;
import java.sql.*;
import java.util.*;

public final class AuctionReceiptStore {
    public static final String SUMMARY_SCHEMA = """
            CREATE TABLE IF NOT EXISTS auction_receipt_summaries (
                auction_id BIGINT PRIMARY KEY, product_code VARCHAR(50) NOT NULL,
                product_name VARCHAR(255) NOT NULL, ended_at DATETIME(3) NOT NULL,
                outcome VARCHAR(16) NOT NULL, winner_display VARCHAR(100) NOT NULL,
                final_price DECIMAL(18,2) NULL, total_bids BIGINT NOT NULL,
                bidder_count BIGINT NOT NULL, extension_count BIGINT NOT NULL,
                extensions_complete BOOLEAN NOT NULL, anonymous_guests BOOLEAN NOT NULL,
                FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE
            )
            """;
    public static final String PERSONAL_SCHEMA = """
            CREATE TABLE IF NOT EXISTS auction_personal_receipts (
                auction_id BIGINT NOT NULL, user_id BIGINT NOT NULL, personal_role VARCHAR(16) NOT NULL,
                PRIMARY KEY (auction_id, user_id), INDEX idx_receipt_owner (user_id, auction_id),
                FOREIGN KEY (auction_id) REFERENCES auction_receipt_summaries(auction_id) ON DELETE CASCADE,
                FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
            )
            """;
    private final JdbcConnectionFactory factory;
    public AuctionReceiptStore(JdbcConnectionFactory factory) { this.factory = factory; }

    public static void initialize(JdbcConnectionFactory factory) {
        try (var connection = factory.openDatabase(); var statement = connection.createStatement()) {
            statement.executeUpdate(SUMMARY_SCHEMA); statement.executeUpdate(PERSONAL_SCHEMA);
        } catch (SQLException exception) { throw new AuctionRepositoryException("Cannot initialize receipts", exception); }
        List<Long> legacy = new ArrayList<>();
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement("""
                SELECT a.auction_id FROM auctions a WHERE a.status <> 'OPEN'
                  AND NOT EXISTS (SELECT 1 FROM auction_receipt_summaries s WHERE s.auction_id = a.auction_id)
                  AND (a.status = 'CANCELLED' OR EXISTS (SELECT 1 FROM auction_results r WHERE r.auction_id = a.auction_id))
                """); var rows = query.executeQuery()) {
            while (rows.next()) { legacy.add(rows.getLong(1)); }
        } catch (SQLException exception) { throw new AuctionRepositoryException("Cannot find legacy receipts", exception); }
        for (long room : legacy) {
            try (var connection = factory.openDatabase()) {
                connection.setAutoCommit(false);
                try { capture(connection, room); connection.commit(); }
                catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
            } catch (SQLException exception) { throw new AuctionRepositoryException("Cannot migrate legacy receipt", exception); }
        }
    }

    public static void capture(Connection connection, long room) throws SQLException {
        if (connection.getAutoCommit()) { throw new SQLException("Receipt capture requires the closure transaction"); }
        try (var lock = connection.prepareStatement("SELECT auction_id FROM auctions WHERE auction_id = ? FOR UPDATE")) {
            lock.setLong(1, room); try (var rows = lock.executeQuery()) { if (!rows.next()) { throw new SQLException("Unknown auction"); } }
        }
        try (var query = connection.prepareStatement("SELECT 1 FROM auction_receipt_summaries WHERE auction_id = ?")) {
            query.setLong(1, room); try (var rows = query.executeQuery()) { if (rows.next()) { return; } }
        }
        long host; Long winner; String outcome;
        try (var query = connection.prepareStatement("""
                SELECT a.host_user_id, a.status, a.anonymous_guests, a.ended_at, a.end_time,
                    p.code, p.name, r.winner_id, r.final_price, r.ended_at AS confirmed_end, u.username
                FROM auctions a JOIN products p ON p.product_id = a.product_id
                LEFT JOIN auction_results r ON r.auction_id = a.auction_id
                LEFT JOIN users u ON u.user_id = r.winner_id WHERE a.auction_id = ?
                """)) {
            query.setLong(1, room);
            try (var rows = query.executeQuery()) {
                if (!rows.next() || "OPEN".equals(rows.getString("status"))) { throw new SQLException("Auction not finalized"); }
                host = rows.getLong("host_user_id"); long raw = rows.getLong("winner_id"); winner = rows.wasNull() ? null : raw;
                boolean cancelled = "CANCELLED".equals(rows.getString("status"));
                if (!cancelled && rows.getTimestamp("confirmed_end") == null) { throw new SQLException("Missing confirmed result"); }
                outcome = cancelled ? "CANCELLED" : winner == null ? "NO_BIDS" : "WON";
                boolean anonymous = rows.getBoolean("anonymous_guests");
                String display = winner == null || cancelled ? "" : anonymous ? alias(connection, room, winner, host) : rows.getString("username");
                var stats = RoomProgressStore.stats(connection, room);
                try (var insert = connection.prepareStatement("""
                        INSERT INTO auction_receipt_summaries(auction_id, product_code, product_name, ended_at,
                            outcome, winner_display, final_price, total_bids, bidder_count, extension_count,
                            extensions_complete, anonymous_guests) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    insert.setLong(1, room); insert.setString(2, rows.getString("code")); insert.setString(3, rows.getString("name"));
                    Timestamp ended = cancelled ? rows.getTimestamp("ended_at") : rows.getTimestamp("confirmed_end");
                    insert.setTimestamp(4, ended == null ? rows.getTimestamp("end_time") : ended);
                    insert.setString(5, outcome); insert.setString(6, display);
                    insert.setBigDecimal(7, "WON".equals(outcome) ? rows.getBigDecimal("final_price") : null);
                    insert.setLong(8, stats.bidCount()); insert.setLong(9, stats.bidderCount()); insert.setLong(10, stats.extensionCount());
                    insert.setBoolean(11, stats.extensionsComplete()); insert.setBoolean(12, anonymous); insert.executeUpdate();
                }
            }
        }
        Set<Long> participants = new LinkedHashSet<>(); Set<Long> bidders = new HashSet<>(); participants.add(host);
        try (var query = connection.prepareStatement("SELECT DISTINCT user_id FROM bids WHERE auction_id = ?")) {
            query.setLong(1, room); try (var rows = query.executeQuery()) { while (rows.next()) { bidders.add(rows.getLong(1)); } }
        }
        participants.addAll(bidders);
        try (var query = connection.prepareStatement("SELECT DISTINCT actor_id FROM room_events WHERE auction_id = ? AND event_type = 'JOINED' AND actor_id IS NOT NULL")) {
            query.setLong(1, room); try (var rows = query.executeQuery()) { while (rows.next()) { participants.add(rows.getLong(1)); } }
        }
        for (long user : participants) {
            String role = user == host ? "HOST" : "WON".equals(outcome) && Objects.equals(winner, user) ? "WINNER" : bidders.contains(user) ? "NOT_WON" : "WATCHED";
            try (var insert = connection.prepareStatement("INSERT INTO auction_personal_receipts(auction_id, user_id, personal_role) VALUES (?, ?, ?)")) {
                insert.setLong(1, room); insert.setLong(2, user); insert.setString(3, role); insert.executeUpdate();
            }
        }
    }

    private static String alias(Connection connection, long room, long user, long host) throws SQLException {
        if (user == host) { return "Ch\u1ee7 ph\u00f2ng"; }
        try (var query = connection.prepareStatement("SELECT ordinal_no FROM room_participants WHERE auction_id = ? AND user_id = ?")) {
            query.setLong(1, room); query.setLong(2, user);
            try (var rows = query.executeQuery()) { if (rows.next()) { return "Kh\u00e1ch #" + rows.getLong(1); } }
        }
        long ordinal;
        try (var query = connection.prepareStatement("SELECT COALESCE(MAX(ordinal_no), 0) + 1 FROM room_participants WHERE auction_id = ?")) {
            query.setLong(1, room); try (var rows = query.executeQuery()) { rows.next(); ordinal = rows.getLong(1); }
        }
        try (var insert = connection.prepareStatement("INSERT INTO room_participants(auction_id, user_id, ordinal_no) VALUES (?, ?, ?)")) {
            insert.setLong(1, room); insert.setLong(2, user); insert.setLong(3, ordinal); insert.executeUpdate();
        }
        return "Kh\u00e1ch #" + ordinal;
    }

    public Map<String, String> receipt(long user, long room) {
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement("""
                SELECT s.*, p.personal_role FROM auction_receipt_summaries s
                JOIN auction_personal_receipts p ON p.auction_id = s.auction_id
                WHERE p.user_id = ? AND s.auction_id = ?
                """)) {
            query.setLong(1, user); query.setLong(2, room);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) { throw new AuctionException(ErrorCode.AUCTION_FORBIDDEN, "Khong co bien nhan ca nhan da chot cho phien nay"); }
                var result = summary(rows); wallet(connection, user, room, result); return result;
            }
        } catch (SQLException exception) { throw new AuctionRepositoryException("Cannot read personal receipt", exception); }
    }

    public Map<String, String> list(long user, String scope, String outcome, String search, long before, int limit) {
        if (!Set.of("PARTICIPATED", "WON", "HOSTED").contains(scope) || !Set.of("ALL", "WON", "NO_BIDS", "CANCELLED").contains(outcome)) {
            throw new IllegalArgumentException("Invalid receipt filter");
        }
        String role = switch (scope) { case "WON" -> "p.personal_role = 'WINNER'"; case "HOSTED" -> "p.personal_role = 'HOST'"; default -> "p.personal_role <> 'HOST'"; };
        String sql = """
                SELECT s.*, p.personal_role FROM auction_receipt_summaries s
                JOIN auction_personal_receipts p ON p.auction_id = s.auction_id
                WHERE p.user_id = ? AND s.auction_id < ? AND
                """ + role + " AND (? = 'ALL' OR s.outcome = ?) AND (LOWER(s.product_name) LIKE ? OR LOWER(s.product_code) LIKE ? OR CAST(s.auction_id AS CHAR(20)) = ?) ORDER BY s.auction_id DESC LIMIT ?";
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement(sql)) {
            query.setLong(1, user); query.setLong(2, before); query.setString(3, outcome); query.setString(4, outcome);
            query.setString(5, "%" + search.toLowerCase(Locale.ROOT) + "%"); query.setString(6, "%" + search.toLowerCase(Locale.ROOT) + "%");
            query.setString(7, search); query.setInt(8, limit + 1);
            Map<String, String> result = new LinkedHashMap<>(); int count = 0; long next = 0; boolean more = false;
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    if (count == limit) { more = true; break; }
                    String prefix = "receipt." + count++ + "."; summary(rows).forEach((key, value) -> result.put(prefix + key, value)); next = rows.getLong("auction_id");
                }
            }
            result.put("count", Integer.toString(count)); result.put("hasMore", Boolean.toString(more)); result.put("nextBeforeId", Long.toString(next)); return result;
        } catch (SQLException exception) { throw new AuctionRepositoryException("Cannot list personal receipts", exception); }
    }

    private static Map<String, String> summary(ResultSet rows) throws SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("auctionId", Long.toString(rows.getLong("auction_id"))); result.put("productCode", rows.getString("product_code"));
        result.put("productName", rows.getString("product_name")); result.put("endedAt", rows.getTimestamp("ended_at").toInstant().toString());
        result.put("outcome", rows.getString("outcome")); result.put("winnerDisplay", rows.getString("winner_display"));
        var price = rows.getBigDecimal("final_price"); result.put("finalPrice", price == null ? "" : price.toPlainString());
        result.put("totalBids", Long.toString(rows.getLong("total_bids"))); result.put("uniqueBidders", Long.toString(rows.getLong("bidder_count")));
        result.put("extensionCount", Long.toString(rows.getLong("extension_count"))); result.put("extensionsComplete", Boolean.toString(rows.getBoolean("extensions_complete")));
        result.put("anonymousGuests", Boolean.toString(rows.getBoolean("anonymous_guests"))); result.put("personalRole", rows.getString("personal_role")); return result;
    }

    private static void wallet(Connection connection, long user, long room, Map<String, String> result) throws SQLException {
        String status = "NONE";
        try (var query = connection.prepareStatement("SELECT status, amount FROM wallet_settlements WHERE auction_id = ? AND (buyer_id = ? OR seller_id = ?)")) {
            query.setLong(1, room); query.setLong(2, user); query.setLong(3, user);
            try (var rows = query.executeQuery()) {
                if (rows.next()) {
                    status = rows.getString("status");
                    if (result.get("finalPrice").isBlank() || rows.getBigDecimal("amount").compareTo(new java.math.BigDecimal(result.get("finalPrice"))) != 0) { status = "REVIEW_REQUIRED"; }
                }
            }
        }
        String held = "0";
        try (var query = connection.prepareStatement("SELECT amount FROM wallet_holds WHERE auction_id = ? AND user_id = ?")) {
            query.setLong(1, room); query.setLong(2, user); try (var rows = query.executeQuery()) { if (rows.next()) { held = rows.getBigDecimal(1).toPlainString(); status = "REVIEW_REQUIRED"; } }
        }
        result.put("heldAmount", held);
        try (var query = connection.prepareStatement("""
                SELECT e.* FROM wallet_entries e WHERE e.user_id = ? AND (
                    e.reference_key IN (?, ?, ?, ?) OR EXISTS (SELECT 1 FROM bids b WHERE b.auction_id = ?
                    AND e.reference_key IN (CONCAT('BID:', b.server_sequence, ':RESERVE'), CONCAT('BID:', b.server_sequence, ':RELEASE'))))
                ORDER BY e.entry_id DESC LIMIT 101
                """)) {
            query.setLong(1, user); query.setString(2, "BUY:" + room); query.setString(3, "SELL:" + room);
            query.setString(4, "LEGACY:BUY:" + room); query.setString(5, "LEGACY:SELL:" + room); query.setLong(6, room);
            int count = 0; boolean more = false; boolean release = false, reserve = false, purchase = false, sale = false, review = false;
            var finalPrice = result.get("finalPrice").isBlank() ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(result.get("finalPrice"));
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    if (count == 100) { more = true; break; }
                    String type = rows.getString("entry_type"); release |= "RELEASE".equals(type); reserve |= "RESERVE".equals(type);
                    purchase |= "PURCHASE".equals(type) && rows.getBigDecimal("amount").compareTo(finalPrice.negate()) == 0
                            && rows.getBigDecimal("reserved_delta").compareTo(finalPrice.negate()) == 0;
                    sale |= "SALE".equals(type) && rows.getBigDecimal("amount").compareTo(finalPrice) == 0
                            && rows.getBigDecimal("reserved_delta").signum() == 0;
                    review |= "LEGACY_REVIEW".equals(type);
                    String prefix = "entry." + count++ + ".";
                    result.put(prefix + "id", Long.toString(rows.getLong("entry_id"))); result.put(prefix + "type", type);
                    result.put(prefix + "amount", rows.getBigDecimal("amount").toPlainString()); result.put(prefix + "reservedDelta", rows.getBigDecimal("reserved_delta").toPlainString());
                    result.put(prefix + "reference", rows.getString("reference_key")); result.put(prefix + "time", rows.getTimestamp("created_at").toInstant().toString());
                }
            }
            String role = result.get("personalRole");
            if (review || (Set.of("WINNER", "HOST").contains(role) && "WON".equals(result.get("outcome"))
                    && (!"PAID".equals(status) || !("WINNER".equals(role) ? purchase : sale)))) { status = "REVIEW_REQUIRED"; }
            if ("NONE".equals(status) && release) { status = "RELEASED"; }
            if ("NONE".equals(status) && reserve) { status = "REVIEW_REQUIRED"; }
            try (var balance = connection.prepareStatement("""
                    SELECT COALESCE(SUM(e.reserved_delta), 0) FROM wallet_entries e WHERE e.user_id = ? AND (
                        e.reference_key IN (?, ?, ?, ?) OR EXISTS (SELECT 1 FROM bids b WHERE b.auction_id = ?
                        AND e.reference_key IN (CONCAT('BID:', b.server_sequence, ':RESERVE'), CONCAT('BID:', b.server_sequence, ':RELEASE'))))
                    """)) {
                balance.setLong(1, user); balance.setString(2, "BUY:" + room); balance.setString(3, "SELL:" + room);
                balance.setString(4, "LEGACY:BUY:" + room); balance.setString(5, "LEGACY:SELL:" + room); balance.setLong(6, room);
                try (var rows = balance.executeQuery()) { rows.next(); if (rows.getBigDecimal(1).signum() != 0) { status = "REVIEW_REQUIRED"; } }
            }
            result.put("walletStatus", status); result.put("entryCount", Integer.toString(count)); result.put("entriesHasMore", Boolean.toString(more));
        }
    }
}
