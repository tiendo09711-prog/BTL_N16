package vn.ptit.btl16.server.social;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.repository.AuctionRepositoryException;
import vn.ptit.btl16.server.auction.service.AuctionException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RoomSocialStore {
    public record Details(String biography, String avatar, long version) { }
    public record Chat(long id, long auctionId, long senderId, String nonce, String body, Instant sentAt, boolean fresh, long replyToId) { }
    private record MemberKey(long auctionId, long userId) { }
    private final JdbcConnectionFactory factory;
    private final Map<MemberKey, Long> aliases = new ConcurrentHashMap<>();
    private final Map<Long, Details> memoryProfiles = new HashMap<>();
    private final List<Chat> memoryChat = new ArrayList<>();

    public RoomSocialStore(JdbcConnectionFactory factory) { this.factory = factory; }
    JdbcConnectionFactory connectionFactory() { return factory; }

    public synchronized boolean containsChats(long auctionId, java.util.Collection<Long> ids) {
        if (ids.isEmpty()) { return true; }
        if (ids.size() > 200) { throw new IllegalArgumentException("Too many message references"); }
        if (factory == null) { return memoryChat.stream().filter(chat -> chat.auctionId() == auctionId && ids.contains(chat.id())).count() == ids.size(); }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement("SELECT COUNT(*) FROM room_chat WHERE auction_id=? AND chat_id IN (" + placeholders + ")")) {
            query.setLong(1, auctionId); int index = 2; for (long id : ids) { query.setLong(index++, id); }
            try (var rows = query.executeQuery()) { rows.next(); return rows.getInt(1) == ids.size(); }
        } catch (SQLException exception) { throw failure(exception); }
    }

    public synchronized Chat findChat(long auctionId, long id) {
        if (factory == null) { return memoryChat.stream().filter(chat -> chat.auctionId() == auctionId && chat.id() == id).findFirst().orElse(null); }
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement("SELECT * FROM room_chat WHERE auction_id=? AND chat_id=?")) {
            query.setLong(1, auctionId); query.setLong(2, id);
            try (var rows = query.executeQuery()) { return rows.next() ? chat(rows, false) : null; }
        } catch (SQLException exception) { throw failure(exception); }
    }

    public synchronized List<Chat> after(long auctionId, long afterId) {
        if (factory == null) { return memoryChat.stream().filter(chat -> chat.auctionId() == auctionId && chat.id() > afterId).limit(50).toList(); }
        List<Chat> values = new ArrayList<>();
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement("SELECT * FROM room_chat WHERE auction_id=? AND chat_id>? ORDER BY chat_id LIMIT 50")) {
            query.setLong(1, auctionId); query.setLong(2, afterId);
            try (var rows = query.executeQuery()) { while (rows.next()) { values.add(chat(rows, false)); } }
            return values;
        } catch (SQLException exception) { throw failure(exception); }
    }

    public long ordinal(long auctionId, long userId, boolean create) {
        MemberKey key = new MemberKey(auctionId, userId);
        Long cached = aliases.get(key);
        if (cached != null) { return cached; }
        if (factory == null) {
            synchronized (aliases) {
                if (!create) { return aliases.getOrDefault(key, 0L); }
                return aliases.computeIfAbsent(key, ignored -> aliases.entrySet().stream()
                        .filter(entry -> entry.getKey().auctionId() == auctionId).mapToLong(Map.Entry::getValue).max().orElse(0) + 1);
            }
        }
        try (Connection connection = factory.openDatabase()) {
            if (create) { connection.setAutoCommit(false); lockAuction(connection, auctionId); }
            long value = 0;
            try (PreparedStatement query = connection.prepareStatement("SELECT ordinal_no FROM room_participants WHERE auction_id=? AND user_id=?")) {
                query.setLong(1, auctionId); query.setLong(2, userId);
                try (ResultSet rows = query.executeQuery()) { if (rows.next()) { value = rows.getLong(1); } }
            }
            if (value == 0 && create) {
                try (PreparedStatement query = connection.prepareStatement("SELECT COALESCE(MAX(ordinal_no),0)+1 FROM room_participants WHERE auction_id=?")) {
                    query.setLong(1, auctionId);
                    try (ResultSet rows = query.executeQuery()) { rows.next(); value = rows.getLong(1); }
                }
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO room_participants(auction_id,user_id,ordinal_no) VALUES(?,?,?)")) {
                    insert.setLong(1, auctionId); insert.setLong(2, userId); insert.setLong(3, value); insert.executeUpdate();
                }
            }
            if (create) { connection.commit(); }
            if (value != 0) { aliases.put(key, value); }
            return value;
        } catch (SQLException exception) { throw failure(exception); }
    }

    public long userForOrdinal(long auctionId, long ordinal) {
        for (var entry : aliases.entrySet()) {
            if (entry.getKey().auctionId() == auctionId && entry.getValue() == ordinal) { return entry.getKey().userId(); }
        }
        if (factory == null) { return 0; }
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement("SELECT user_id FROM room_participants WHERE auction_id=? AND ordinal_no=?")) {
            query.setLong(1, auctionId); query.setLong(2, ordinal);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) { return 0; }
                long userId = rows.getLong(1); aliases.put(new MemberKey(auctionId, userId), ordinal); return userId;
            }
        } catch (SQLException exception) { throw failure(exception); }
    }

    public synchronized Details details(long userId) {
        if (factory == null) { return memoryProfiles.getOrDefault(userId, new Details("", "", 0)); }
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement("SELECT biography,avatar_base64,profile_version FROM user_public_profiles WHERE user_id=?")) {
            query.setLong(1, userId);
            try (var rows = query.executeQuery()) { return rows.next() ? new Details(rows.getString(1), rows.getString(2), rows.getLong(3)) : new Details("", "", 0); }
        } catch (SQLException exception) { throw failure(exception); }
    }

    public synchronized Details saveDetails(long userId, String biography, String avatar) {
        if (factory == null) {
            Details old = details(userId); Details next = new Details(biography, avatar == null ? old.avatar() : avatar, old.version() + 1);
            memoryProfiles.put(userId, next); return next;
        }
        try (var connection = factory.openDatabase()) {
            connection.setAutoCommit(false);
            try (var lock = connection.prepareStatement("SELECT user_id FROM users WHERE user_id=? FOR UPDATE")) {
                lock.setLong(1, userId); try (var rows = lock.executeQuery()) { if (!rows.next()) { throw new SQLException("User not found"); } }
            }
            Details previous = null;
            try (var query = connection.prepareStatement("SELECT biography,avatar_base64,profile_version FROM user_public_profiles WHERE user_id=?")) {
                query.setLong(1, userId); try (var rows = query.executeQuery()) { if (rows.next()) { previous = new Details(rows.getString(1), rows.getString(2), rows.getLong(3)); } }
            }
            Details next = new Details(biography, avatar == null ? previous == null ? "" : previous.avatar() : avatar, previous == null ? 1 : previous.version() + 1);
            String sql = previous == null ? "INSERT INTO user_public_profiles(biography,avatar_base64,profile_version,user_id) VALUES(?,?,?,?)"
                    : "UPDATE user_public_profiles SET biography=?,avatar_base64=?,profile_version=? WHERE user_id=?";
            try (var write = connection.prepareStatement(sql)) {
                write.setString(1, next.biography()); write.setString(2, next.avatar()); write.setLong(3, next.version()); write.setLong(4, userId); write.executeUpdate();
            }
            connection.commit(); return next;
        } catch (SQLException exception) { throw failure(exception); }
    }

    public synchronized List<Chat> recent(long auctionId) {
        if (factory == null) { return memoryChat.stream().filter(chat -> chat.auctionId() == auctionId).sorted(java.util.Comparator.comparingLong(Chat::id).reversed()).limit(50).toList(); }
        List<Chat> values = new ArrayList<>();
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement("SELECT * FROM room_chat WHERE auction_id=? ORDER BY chat_id DESC LIMIT 50")) {
            query.setLong(1, auctionId); try (var rows = query.executeQuery()) { while (rows.next()) { values.add(chat(rows, false)); } }
            return values;
        } catch (SQLException exception) { throw failure(exception); }
    }

    public synchronized Chat append(long auctionId, long sender, String nonce, String body, Instant now) {
        return append(auctionId, sender, nonce, body, now, 0);
    }

    public synchronized Chat append(long auctionId, long sender, String nonce, String body, Instant now, long replyToId) {
        if (factory == null) {
            for (Chat entry : memoryChat) {
                if (entry.auctionId() == auctionId && entry.senderId() == sender && entry.nonce().equals(nonce)) { return duplicate(entry, body, replyToId); }
            }
            memoryChat.stream().filter(entry -> entry.auctionId() == auctionId && entry.senderId() == sender)
                    .max(java.util.Comparator.comparingLong(Chat::id)).ifPresent(entry -> requireDelay(entry.sentAt(), now));
            Chat created = new Chat(memoryChat.size() + 1L, auctionId, sender, nonce, body, now, true, replyToId); memoryChat.add(created); return created;
        }
        try (var connection = factory.openDatabase()) {
            connection.setAutoCommit(false); lockAuction(connection, auctionId);
            try (var query = connection.prepareStatement("SELECT * FROM room_chat WHERE auction_id=? AND sender_id=? AND client_nonce=?")) {
                query.setLong(1, auctionId); query.setLong(2, sender); query.setString(3, nonce);
                try (var rows = query.executeQuery()) { if (rows.next()) { Chat existing = duplicate(chat(rows, false), body, replyToId); connection.commit(); return existing; } }
            }
            try (var query = connection.prepareStatement("SELECT sent_at FROM room_chat WHERE auction_id=? AND sender_id=? ORDER BY chat_id DESC LIMIT 1")) {
                query.setLong(1, auctionId); query.setLong(2, sender);
                try (var rows = query.executeQuery()) { if (rows.next()) { requireDelay(rows.getTimestamp(1).toInstant(), now); } }
            }
            long id;
            try (var insert = connection.prepareStatement("INSERT INTO room_chat(auction_id,sender_id,client_nonce,body,sent_at,reply_to_id) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                insert.setLong(1, auctionId); insert.setLong(2, sender); insert.setString(3, nonce); insert.setString(4, body); insert.setTimestamp(5, Timestamp.from(now)); insert.setLong(6, replyToId); insert.executeUpdate();
                try (var keys = insert.getGeneratedKeys()) { if (!keys.next()) { throw new SQLException("Missing chat id"); } id = keys.getLong(1); }
            }
            connection.commit(); return new Chat(id, auctionId, sender, nonce, body, now, true, replyToId);
        } catch (SQLException exception) { throw failure(exception); }
    }

    private static void lockAuction(Connection connection, long auctionId) throws SQLException {
        try (var lock = connection.prepareStatement("SELECT auction_id FROM auctions WHERE auction_id=? FOR UPDATE")) {
            lock.setLong(1, auctionId); try (var rows = lock.executeQuery()) { if (!rows.next()) { throw new SQLException("Auction not found"); } }
        }
    }

    private static Chat chat(ResultSet rows, boolean fresh) throws SQLException {
        return new Chat(rows.getLong("chat_id"), rows.getLong("auction_id"), rows.getLong("sender_id"), rows.getString("client_nonce"), rows.getString("body"), rows.getTimestamp("sent_at").toInstant(), fresh, rows.getLong("reply_to_id"));
    }

    private static Chat duplicate(Chat entry, String body, long replyToId) {
        if (!entry.body().equals(body) || entry.replyToId() != replyToId) { throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Ma tin nhan da duoc dung cho noi dung khac"); }
        return new Chat(entry.id(), entry.auctionId(), entry.senderId(), entry.nonce(), entry.body(), entry.sentAt(), false, entry.replyToId());
    }

    private static void requireDelay(Instant previous, Instant now) {
        if (Duration.between(previous, now).toMillis() < 750) { throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Hay cho mot giay truoc khi gui tin tiep theo"); }
    }

    private static AuctionRepositoryException failure(SQLException exception) { return new AuctionRepositoryException("Room social storage failed", exception); }
}
