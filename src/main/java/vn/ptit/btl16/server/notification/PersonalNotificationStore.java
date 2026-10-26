package vn.ptit.btl16.server.notification;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.repository.AuctionRepositoryException;
import vn.ptit.btl16.server.auction.service.AuctionException;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class PersonalNotificationStore {
    public static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS personal_notifications (
                notification_id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
                source_key VARCHAR(160) NOT NULL UNIQUE, kind VARCHAR(32) NOT NULL,
                auction_id BIGINT NULL, reference_id VARCHAR(64) NOT NULL,
                created_at DATETIME(3) NOT NULL, read_at DATETIME(3) NULL,
                INDEX idx_notification_owner (user_id, notification_id),
                FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE
            )
            """;
    private final JdbcConnectionFactory factory;
    public PersonalNotificationStore(JdbcConnectionFactory factory) { this.factory = factory; }
    public static void initialize(JdbcConnectionFactory factory) {
        try (var connection = factory.openDatabase(); var statement = connection.createStatement()) { statement.executeUpdate(SCHEMA); }
        catch (SQLException error) { throw new AuctionRepositoryException("Cannot initialize personal notifications", error); }
    }
    public static void save(Connection connection, long user, String source, String kind, Long room, String reference, Instant now) throws SQLException {
        try (var insert = connection.prepareStatement("""
                INSERT INTO personal_notifications(user_id,source_key,kind,auction_id,reference_id,created_at)
                VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE source_key=VALUES(source_key)
                """)) {
            insert.setLong(1, user); insert.setString(2, source); insert.setString(3, kind);
            if (room == null) { insert.setNull(4, Types.BIGINT); } else { insert.setLong(4, room); }
            insert.setString(5, reference); insert.setTimestamp(6, Timestamp.from(now)); insert.executeUpdate();
        }
    }
    public static void result(Connection connection, long room, Instant now) throws SQLException {
        try (var query = connection.prepareStatement("SELECT user_id FROM auction_personal_receipts WHERE auction_id=?")) {
            query.setLong(1, room);
            try (var rows = query.executeQuery()) { while (rows.next()) { long user = rows.getLong(1); save(connection, user, "RESULT:" + room + ":" + user, "AUCTION_RESULT", room, "", now); } }
        }
    }
    public Map<String, String> list(long user, long before, boolean unreadOnly, int limit) {
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement("""
                SELECT * FROM personal_notifications WHERE user_id=? AND notification_id<?
                """ + (unreadOnly ? " AND read_at IS NULL" : "") + " ORDER BY notification_id DESC LIMIT ?")) {
            query.setLong(1, user); query.setLong(2, before); query.setInt(3, limit + 1);
            Map<String, String> data = new LinkedHashMap<>(); int count = 0; long next = 0; boolean more = false;
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    if (count == limit) { more = true; break; }
                    String prefix = "notification." + count++ + "."; next = rows.getLong("notification_id");
                    data.put(prefix + "id", Long.toString(next)); data.put(prefix + "kind", rows.getString("kind"));
                    long room = rows.getLong("auction_id"); data.put(prefix + "auctionId", rows.wasNull() ? "" : Long.toString(room));
                    data.put(prefix + "reference", rows.getString("reference_id")); data.put(prefix + "createdAt", rows.getTimestamp("created_at").toInstant().toString());
                    var read = rows.getTimestamp("read_at"); data.put(prefix + "read", Boolean.toString(read != null));
                }
            }
            try (var unread = connection.prepareStatement("SELECT COUNT(*) FROM personal_notifications WHERE user_id=? AND read_at IS NULL")) {
                unread.setLong(1, user); try (var rows = unread.executeQuery()) { rows.next(); data.put("unreadCount", Long.toString(rows.getLong(1))); }
            }
            data.put("count", Integer.toString(count)); data.put("hasMore", Boolean.toString(more)); data.put("nextBeforeId", Long.toString(next)); return data;
        } catch (SQLException error) { throw new AuctionRepositoryException("Cannot read personal notifications", error); }
    }
    public void read(long user, long notification, boolean read) {
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement("UPDATE personal_notifications SET read_at="
                + (read ? "COALESCE(read_at,?)" : "NULL") + " WHERE user_id=? AND notification_id=?")) {
            int position = 1; if (read) { query.setTimestamp(position++, Timestamp.from(Instant.now())); }
            query.setLong(position++, user); query.setLong(position, notification);
            if (query.executeUpdate() != 1) {
                try (var own = connection.prepareStatement("SELECT 1 FROM personal_notifications WHERE user_id=? AND notification_id=?")) {
                    own.setLong(1, user); own.setLong(2, notification);
                    try (var rows = own.executeQuery()) { if (!rows.next()) { throw new AuctionException(ErrorCode.AUCTION_FORBIDDEN, "Khong co thong bao ca nhan nay"); } }
                }
            }
        } catch (SQLException error) { throw new AuctionRepositoryException("Cannot mark personal notification", error); }
    }
}
