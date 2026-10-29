package vn.ptit.btl16.server.social;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.repository.AuctionRepositoryException;
import vn.ptit.btl16.server.auction.service.AuctionException;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Friendship and private-message persistence. Every pair mutation uses the same ordered user locks. */
public final class FriendsStore {
    public record Link(long low, long high, long requester, boolean accepted, Instant updated) {
        public long other(long actor) { return actor == low ? high : low; }
        public String state(long actor) { return accepted ? "FRIEND" : requester == actor ? "OUTGOING" : "INCOMING"; }
    }
    public record Message(long id, long sender, long recipient, String nonce, String body, Instant time, boolean fresh) { }
    private final JdbcConnectionFactory factory;
    private final Map<String, Link> memoryLinks = new HashMap<>();
    private final List<Message> memoryMessages = new ArrayList<>();
    private final Set<Long> memoryRead = new HashSet<>();

    public FriendsStore(JdbcConnectionFactory factory) { this.factory = factory; }

    public static void initialize(JdbcConnectionFactory factory) {
        try (var connection = factory.openDatabase(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS friendships (
                        low_user_id BIGINT NOT NULL, high_user_id BIGINT NOT NULL,
                        requested_by BIGINT NOT NULL, accepted BOOLEAN NOT NULL DEFAULT FALSE,
                        updated_at DATETIME(3) NOT NULL,
                        PRIMARY KEY(low_user_id, high_user_id), INDEX idx_friends_high(high_user_id),
                        FOREIGN KEY(low_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                        FOREIGN KEY(high_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                        FOREIGN KEY(requested_by) REFERENCES users(user_id) ON DELETE CASCADE
                    ) ENGINE=InnoDB
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS private_messages (
                        message_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        sender_id BIGINT NOT NULL, recipient_id BIGINT NOT NULL,
                        client_nonce VARCHAR(80) NOT NULL, body VARCHAR(2000) NOT NULL,
                        sent_at DATETIME(3) NOT NULL, read_at DATETIME(3) NULL,
                        UNIQUE(sender_id, client_nonce), INDEX idx_pm_pair(sender_id, recipient_id, message_id),
                        INDEX idx_pm_unread(recipient_id, read_at, sender_id),
                        FOREIGN KEY(sender_id) REFERENCES users(user_id) ON DELETE CASCADE,
                        FOREIGN KEY(recipient_id) REFERENCES users(user_id) ON DELETE CASCADE
                    ) ENGINE=InnoDB
                    """);
        } catch (SQLException error) { throw failure(error); }
    }

    public synchronized List<Link> list(long user) {
        if (factory == null) { return memoryLinks.values().stream().filter(link -> link.low == user || link.high == user)
                .sorted(Comparator.comparing(Link::updated).reversed()).toList(); }
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement(
                "SELECT * FROM friendships WHERE low_user_id=? OR high_user_id=? ORDER BY updated_at DESC")) {
            query.setLong(1, user); query.setLong(2, user);
            List<Link> result = new ArrayList<>();
            try (var rows = query.executeQuery()) { while (rows.next()) { result.add(link(rows)); } }
            return result;
        } catch (SQLException error) { throw failure(error); }
    }

    public synchronized Link find(long user, long other) {
        if (factory == null) { return memoryLinks.get(key(user, other)); }
        return transaction(connection -> find(connection, user, other));
    }

    public synchronized Link request(long actor, long other) {
        requirePair(actor, other);
        if (factory == null) { return memoryLinks.computeIfAbsent(key(actor, other), ignored -> new Link(Math.min(actor, other), Math.max(actor, other), actor, false, Instant.now())); }
        return transaction(connection -> {
            lock(connection, actor, other);
            Link existing = find(connection, actor, other);
            if (existing != null) { return existing; }
            Link created = new Link(Math.min(actor, other), Math.max(actor, other), actor, false, Instant.now());
            try (var insert = connection.prepareStatement("INSERT INTO friendships(low_user_id,high_user_id,requested_by,accepted,updated_at) VALUES(?,?,?,FALSE,?)")) {
                insert.setLong(1, created.low); insert.setLong(2, created.high); insert.setLong(3, actor);
                insert.setTimestamp(4, Timestamp.from(created.updated)); insert.executeUpdate();
            }
            return created;
        });
    }

    public synchronized void respond(long actor, long other, String action) {
        requirePair(actor, other);
        if (!Set.of("accept", "decline", "cancel").contains(action)) { throw invalid("Thao tác lời mời không hợp lệ"); }
        if (factory == null) {
            Link existing = memoryLinks.get(key(actor, other)); validateResponse(existing, actor, action);
            if (existing.accepted) { return; }
            if (action.equals("accept")) { memoryLinks.put(key(actor, other), new Link(existing.low, existing.high, existing.requester, true, Instant.now())); }
            else { memoryLinks.remove(key(actor, other)); }
            return;
        }
        transaction(connection -> {
            lock(connection, actor, other);
            Link existing = find(connection, actor, other); validateResponse(existing, actor, action);
            if (existing.accepted) { return null; }
            String sql = action.equals("accept") ? "UPDATE friendships SET accepted=TRUE, updated_at=CURRENT_TIMESTAMP(3) WHERE low_user_id=? AND high_user_id=?"
                    : "DELETE FROM friendships WHERE low_user_id=? AND high_user_id=?";
            try (var query = connection.prepareStatement(sql)) { query.setLong(1, existing.low); query.setLong(2, existing.high); query.executeUpdate(); }
            return null;
        });
    }

    public synchronized Map<Long, Integer> unread(long user) {
        Map<Long, Integer> result = new HashMap<>();
        if (factory == null) {
            memoryMessages.stream().filter(message -> message.recipient == user && !memoryRead.contains(message.id))
                    .forEach(message -> result.merge(message.sender, 1, Integer::sum));
            return result;
        }
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement(
                "SELECT sender_id, COUNT(*) FROM private_messages WHERE recipient_id=? AND read_at IS NULL GROUP BY sender_id")) {
            query.setLong(1, user);
            try (var rows = query.executeQuery()) { while (rows.next()) { result.put(rows.getLong(1), rows.getInt(2)); } }
            return result;
        } catch (SQLException error) { throw failure(error); }
    }

    public synchronized List<Message> history(long actor, long other, long before, long after, boolean read) {
        requirePair(actor, other);
        if (before < 0 || after < 0 || (before > 0 && after > 0)) { throw invalid("Mốc tin nhắn không hợp lệ"); }
        if (factory == null) {
            requireFriends(memoryLinks.get(key(actor, other)));
            var stream = memoryMessages.stream().filter(message -> pair(message, actor, other) && (before == 0 || message.id < before) && message.id > after);
            List<Message> selected = new ArrayList<>(stream.sorted(after > 0 ? Comparator.comparingLong(Message::id) : Comparator.comparingLong(Message::id).reversed()).limit(50).toList());
            selected.sort(Comparator.comparingLong(Message::id));
            if (read && before == 0) { long through = selected.stream().mapToLong(Message::id).max().orElse(after);
                memoryMessages.stream().filter(message -> message.sender == other && message.recipient == actor && message.id <= through).forEach(message -> memoryRead.add(message.id)); }
            return selected;
        }
        return transaction(connection -> {
            lock(connection, actor, other); requireFriends(find(connection, actor, other));
            String sql = "SELECT * FROM private_messages WHERE ((sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?))"
                    + (before > 0 ? " AND message_id<?" : after > 0 ? " AND message_id>?" : "")
                    + " ORDER BY message_id " + (after > 0 ? "ASC" : "DESC") + " LIMIT 50";
            List<Message> messages = new ArrayList<>();
            try (var query = connection.prepareStatement(sql)) {
                query.setLong(1, actor); query.setLong(2, other); query.setLong(3, other); query.setLong(4, actor);
                if (before > 0 || after > 0) { query.setLong(5, before > 0 ? before : after); }
                try (var rows = query.executeQuery()) { while (rows.next()) { messages.add(message(rows, false)); } }
            }
            messages.sort(Comparator.comparingLong(Message::id));
            if (read && before == 0) {
                long through = messages.stream().mapToLong(Message::id).max().orElse(after);
                try (var update = connection.prepareStatement("UPDATE private_messages SET read_at=CURRENT_TIMESTAMP(3) WHERE recipient_id=? AND sender_id=? AND read_at IS NULL AND message_id<=?")) {
                    update.setLong(1, actor); update.setLong(2, other); update.setLong(3, through); update.executeUpdate();
                }
            }
            return messages;
        });
    }

    public synchronized Message send(long actor, long other, String nonce, String body) {
        requirePair(actor, other);
        String text = body == null ? "" : body.strip();
        if (nonce == null || !nonce.matches("[A-Za-z0-9_-]{8,80}") || text.isBlank() || text.codePointCount(0, text.length()) > 2000
                || text.codePoints().anyMatch(value -> (Character.isISOControl(value) && value != '\n' && value != '\t'))) {
            throw invalid("Tin nhắn cần 1–2000 ký tự và mã gửi hợp lệ");
        }
        if (factory == null) {
            requireFriends(memoryLinks.get(key(actor, other)));
            for (var existing : memoryMessages) { if (existing.sender == actor && existing.nonce.equals(nonce)) { return duplicate(existing, other, text); } }
            Message created = new Message(memoryMessages.size() + 1L, actor, other, nonce, text, Instant.now(), true);
            memoryMessages.add(created); return created;
        }
        return transaction(connection -> {
            lock(connection, actor, other); requireFriends(find(connection, actor, other));
            try (var query = connection.prepareStatement("SELECT * FROM private_messages WHERE sender_id=? AND client_nonce=?")) {
                query.setLong(1, actor); query.setString(2, nonce);
                try (var rows = query.executeQuery()) { if (rows.next()) { return duplicate(message(rows, false), other, text); } }
            }
            Instant now = Instant.now();
            try (var insert = connection.prepareStatement("INSERT INTO private_messages(sender_id,recipient_id,client_nonce,body,sent_at) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                insert.setLong(1, actor); insert.setLong(2, other); insert.setString(3, nonce); insert.setString(4, text); insert.setTimestamp(5, Timestamp.from(now)); insert.executeUpdate();
                try (var rows = insert.getGeneratedKeys()) { if (!rows.next()) { throw new SQLException("Missing private message ID"); }
                    return new Message(rows.getLong(1), actor, other, nonce, text, now, true); }
            }
        });
    }

    private static void validateResponse(Link link, long actor, String action) {
        if (link == null) { throw invalid("Lời mời không còn tồn tại"); }
        if (link.accepted && action.equals("accept") && link.requester != actor) { return; }
        if (link.accepted || (action.equals("cancel") ? link.requester != actor : link.requester == actor)) {
            throw new AuctionException(ErrorCode.AUCTION_FORBIDDEN, "Bạn không có quyền xử lý lời mời này");
        }
    }
    private static void requireFriends(Link link) { if (link == null || !link.accepted) { throw new AuctionException(ErrorCode.AUCTION_FORBIDDEN, "Cần chấp nhận kết bạn trước khi nhắn tin"); } }
    private static void requirePair(long actor, long other) { if (actor <= 0 || other <= 0 || actor == other) { throw invalid("Hãy chọn một tài khoản khác"); } }
    private static String key(long first, long second) { return Math.min(first, second) + ":" + Math.max(first, second); }
    private static boolean pair(Message message, long first, long second) { return message.sender == first && message.recipient == second || message.sender == second && message.recipient == first; }
    private static Message duplicate(Message message, long recipient, String body) {
        if (message.recipient != recipient || !message.body.equals(body)) { throw invalid("Mã gửi đã dùng cho một tin nhắn khác"); }
        return new Message(message.id, message.sender, message.recipient, message.nonce, message.body, message.time, false);
    }
    private static Link link(ResultSet rows) throws SQLException { return new Link(rows.getLong("low_user_id"), rows.getLong("high_user_id"), rows.getLong("requested_by"), rows.getBoolean("accepted"), rows.getTimestamp("updated_at").toInstant()); }
    private static Message message(ResultSet rows, boolean fresh) throws SQLException { return new Message(rows.getLong("message_id"), rows.getLong("sender_id"), rows.getLong("recipient_id"), rows.getString("client_nonce"), rows.getString("body"), rows.getTimestamp("sent_at").toInstant(), fresh); }
    private static Link find(Connection connection, long actor, long other) throws SQLException {
        try (var query = connection.prepareStatement("SELECT * FROM friendships WHERE low_user_id=? AND high_user_id=?")) {
            query.setLong(1, Math.min(actor, other)); query.setLong(2, Math.max(actor, other));
            try (var rows = query.executeQuery()) { return rows.next() ? link(rows) : null; }
        }
    }
    private static void lock(Connection connection, long actor, long other) throws SQLException {
        for (long user : new long[]{Math.min(actor, other), Math.max(actor, other)}) {
            try (var query = connection.prepareStatement("SELECT active FROM users WHERE user_id=? FOR UPDATE")) {
                query.setLong(1, user);
                try (var rows = query.executeQuery()) { if (!rows.next() || !rows.getBoolean(1)) { throw invalid("Tài khoản không còn hoạt động"); } }
            }
        }
    }
    @FunctionalInterface private interface Operation<T> { T execute(Connection connection) throws SQLException; }
    private <T> T transaction(Operation<T> operation) {
        try (var connection = factory.openDatabase()) {
            connection.setAutoCommit(false);
            try { T value = operation.execute(connection); connection.commit(); return value; }
            catch (SQLException | RuntimeException error) { connection.rollback(); throw error; }
        } catch (SQLException error) { throw failure(error); }
    }
    private static AuctionException invalid(String message) { return new AuctionException(ErrorCode.VALIDATION_ERROR, message); }
    private static AuctionRepositoryException failure(SQLException error) { return new AuctionRepositoryException("Không thể lưu dữ liệu bạn bè / tin nhắn", error); }
}
