package vn.ptit.btl16.server.auction.repository;

import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.model.BidRecord;
import vn.ptit.btl16.server.auction.model.RoomProgress;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class RoomProgressStore {
    private final JdbcConnectionFactory factory;
    public RoomProgressStore(JdbcConnectionFactory factory) { this.factory = factory; }

    public static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS room_events (
                event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                auction_id BIGINT NOT NULL,
                event_type VARCHAR(32) NOT NULL,
                actor_id BIGINT NULL,
                amount DECIMAL(19,2) NULL,
                detail VARCHAR(100) NOT NULL,
                occurred_at DATETIME(3) NOT NULL,
                INDEX idx_room_events_history (auction_id, event_id),
                FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE
            )
            """;

    public static void initialize(JdbcConnectionFactory factory) {
        try (var connection = factory.openDatabase(); var statement = connection.createStatement()) {
            statement.executeUpdate(SCHEMA);
        } catch (SQLException exception) { throw new AuctionRepositoryException("Cannot initialize room events", exception); }
    }

    public static void append(Connection connection, long room, String type, Long actor,
                              BigDecimal amount, String detail, Instant time) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO room_events(auction_id, event_type, actor_id, amount, detail, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, room); statement.setString(2, type);
            if (actor == null) { statement.setNull(3, Types.BIGINT); } else { statement.setLong(3, actor); }
            statement.setBigDecimal(4, amount); statement.setString(5, detail == null ? "" : detail);
            statement.setTimestamp(6, Timestamp.from(time)); statement.executeUpdate();
        }
    }

    public void presence(long room, String type, long actor, Instant time) {
        try (var connection = factory.openDatabase()) {
            connection.setAutoCommit(false);
            try {
                try (var lock = connection.prepareStatement("SELECT auction_id FROM auctions WHERE auction_id = ? FOR UPDATE")) {
                    lock.setLong(1, room);
                    try (var result = lock.executeQuery()) { if (!result.next()) { throw new SQLException("Unknown room"); } }
                }
                append(connection, room, type, actor, null, "", time); connection.commit();
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw new AuctionRepositoryException("Cannot save presence event", exception); }
    }

    public RoomProgress.Stats stats(long room) {
        try (var connection = factory.openDatabase()) {
            return stats(connection, room);
        } catch (SQLException exception) { throw new AuctionRepositoryException("Cannot load full-session statistics", exception); }
    }

    public static RoomProgress.Stats stats(Connection connection, long room) throws SQLException {
        String sql = """
                SELECT a.current_price - a.start_price AS increase,
                    (SELECT COUNT(*) FROM bids b WHERE b.auction_id = a.auction_id) AS bid_count,
                    (SELECT COUNT(DISTINCT b.user_id) FROM bids b WHERE b.auction_id = a.auction_id) AS bidder_count,
                    (SELECT COALESCE(MAX(b.server_sequence), 0) FROM bids b WHERE b.auction_id = a.auction_id) AS last_bid,
                    (SELECT COUNT(*) FROM room_events e WHERE e.auction_id = a.auction_id
                        AND e.event_type IN ('EXTENDED_AUTO', 'EXTENDED_MANUAL')) AS extensions,
                    (SELECT COUNT(*) FROM room_events e WHERE e.auction_id = a.auction_id
                        AND e.event_type = 'CREATED') AS complete
                FROM auctions a WHERE a.auction_id = ?
                """;
        try (var query = connection.prepareStatement(sql)) {
            query.setLong(1, room);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) { throw new SQLException("Unknown room"); }
                return new RoomProgress.Stats(rows.getLong("bid_count"), rows.getLong("bidder_count"),
                        rows.getLong("extensions"), rows.getLong("complete") > 0,
                        rows.getBigDecimal("increase"), rows.getLong("last_bid"));
            }
        }
    }

    public List<RoomProgress.HistoryBid> history(long room, long before, long through, Long user, int limit) {
        String sql = """
                SELECT b.*, u.username, b.amount - COALESCE(
                    (SELECT previous.amount FROM bids previous WHERE previous.auction_id = b.auction_id
                        AND previous.server_sequence < b.server_sequence ORDER BY previous.server_sequence DESC LIMIT 1),
                    a.start_price) AS increase
                FROM bids b JOIN users u ON u.user_id = b.user_id JOIN auctions a ON a.auction_id = b.auction_id
                WHERE b.auction_id = ? AND b.server_sequence < ? AND b.server_sequence <= ?
                """ + (user == null ? "" : " AND b.user_id = ?") + " ORDER BY b.server_sequence DESC LIMIT ?";
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement(sql)) {
            query.setLong(1, room); query.setLong(2, before); query.setLong(3, through);
            int position = 4;
            if (user != null) { query.setLong(position++, user); }
            query.setInt(position, limit);
            try (var rows = query.executeQuery()) {
                List<RoomProgress.HistoryBid> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(new RoomProgress.HistoryBid(new BidRecord(rows.getLong("bid_id"), room,
                            rows.getLong("user_id"), rows.getString("username"), rows.getBigDecimal("amount"),
                            rows.getLong("server_sequence"), rows.getTimestamp("created_at").toInstant()), rows.getBigDecimal("increase")));
                }
                return result;
            }
        } catch (SQLException exception) { throw new AuctionRepositoryException("Cannot load bid page", exception); }
    }

    public List<RoomProgress.Event> events(long room, long before, long after, int limit) {
        String sql = """
                SELECT e.*, u.username FROM room_events e LEFT JOIN users u ON u.user_id = e.actor_id
                WHERE e.auction_id = ? AND e.event_id < ? AND e.event_id > ? ORDER BY e.event_id
                """ + (after > 0 ? " ASC" : " DESC") + " LIMIT ?";
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement(sql)) {
            query.setLong(1, room); query.setLong(2, before); query.setLong(3, after); query.setInt(4, limit);
            try (var rows = query.executeQuery()) {
                List<RoomProgress.Event> result = new ArrayList<>();
                while (rows.next()) {
                    long actor = rows.getLong("actor_id"); Long user = rows.wasNull() ? null : actor;
                    result.add(new RoomProgress.Event(rows.getLong("event_id"), room, rows.getString("event_type"),
                            user, rows.getString("username"), rows.getBigDecimal("amount"), rows.getString("detail"),
                            rows.getTimestamp("occurred_at").toInstant()));
                }
                return result;
            }
        } catch (SQLException exception) { throw new AuctionRepositoryException("Cannot load room events", exception); }
    }
}
