package vn.ptit.btl16.server.social;

import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.repository.AuctionRepositoryException;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.common.protocol.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class RoomInteractionStore {
    public static final List<String> EMOJI = List.of("LIKE", "HEART", "LAUGH", "CLAP");
    public record State(long revision, String announcement, Instant updatedAt) { }
    public record Mute(String reason, Instant until) {
        public boolean active(Instant now) { return until != null && now.isBefore(until); }
    }
    public record Reaction(String emoji, int count, boolean mine) { }
    private record Actor(long room, long user) { }
    private record Receipt(long room, long user, String nonce) { }
    private record Request(String signature, Instant time) { }
    private record Vote(long chat, long user, String emoji) { }
    @FunctionalInterface private interface Write { void run(Connection connection) throws SQLException; }
    private final JdbcConnectionFactory factory;
    private final Map<Long, State> states = new HashMap<>();
    private final Map<Actor, Mute> mutes = new HashMap<>();
    private final Map<Receipt, Request> receipts = new HashMap<>();
    private final Set<Vote> votes = new HashSet<>();
    public RoomInteractionStore(JdbcConnectionFactory factory) { this.factory = factory; }

    public synchronized State state(long room) {
        if (factory == null) { return states.getOrDefault(room, new State(0, "", Instant.EPOCH)); }
        try (var connection = factory.openDatabase()) { return state(connection, room); }
        catch (SQLException exception) { throw failure(exception); }
    }

    private State state(Connection connection, long room) throws SQLException {
        try (var query = connection.prepareStatement("SELECT revision,announcement,updated_at FROM room_interaction_state WHERE auction_id=?")) {
            query.setLong(1, room);
            try (var rows = query.executeQuery()) { return rows.next() ? new State(rows.getLong(1), rows.getString(2), rows.getTimestamp(3).toInstant()) : new State(0, "", Instant.EPOCH); }
        }
    }

    public synchronized Mute mute(long room, long user) {
        if (factory == null) { return mutes.getOrDefault(new Actor(room, user), new Mute("", null)); }
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement("SELECT reason,until_at FROM room_chat_mutes WHERE auction_id=? AND user_id=?")) {
            query.setLong(1, room); query.setLong(2, user);
            try (var rows = query.executeQuery()) { return rows.next() ? new Mute(rows.getString(1), rows.getTimestamp(2).toInstant()) : new Mute("", null); }
        } catch (SQLException exception) { throw failure(exception); }
    }

    public synchronized boolean announcement(long room, long actor, String nonce, String body, Instant now) {
        return mutate(room, actor, nonce, "announcement\n" + body, now, connection -> {
            if (connection == null) { State old = state(room); states.put(room, new State(old.revision(), body, now)); }
            else { try (var write = connection.prepareStatement("UPDATE room_interaction_state SET announcement=?,updated_at=? WHERE auction_id=?")) {
                write.setString(1, body); write.setTimestamp(2, Timestamp.from(now)); write.setLong(3, room); write.executeUpdate();
            } }
        });
    }

    public synchronized boolean setMute(long room, long actor, String nonce, long target, String reason, int seconds, Instant now) {
        return mutate(room, actor, nonce, "mute\n" + target + "\n" + seconds + "\n" + reason, now, connection -> {
            if (connection == null) {
                if (seconds == 0) { mutes.remove(new Actor(room, target)); }
                else { mutes.put(new Actor(room, target), new Mute(reason, now.plusSeconds(seconds))); }
            } else {
                try (var delete = connection.prepareStatement("DELETE FROM room_chat_mutes WHERE auction_id=? AND user_id=?")) {
                    delete.setLong(1, room); delete.setLong(2, target); delete.executeUpdate();
                }
                if (seconds > 0) { try (var insert = connection.prepareStatement("INSERT INTO room_chat_mutes(auction_id,user_id,reason,until_at) VALUES(?,?,?,?)")) {
                    insert.setLong(1, room); insert.setLong(2, target); insert.setString(3, reason); insert.setTimestamp(4, Timestamp.from(now.plusSeconds(seconds))); insert.executeUpdate();
                } }
            }
        });
    }

    public synchronized boolean reaction(long room, long actor, String nonce, long chat, String emoji, boolean active, Instant now) {
        if (!EMOJI.contains(emoji)) { throw new IllegalArgumentException("Unsupported reaction"); }
        return mutate(room, actor, nonce, "reaction\n" + chat + "\n" + emoji + "\n" + active, now, connection -> {
            Vote vote = new Vote(chat, actor, emoji);
            if (connection == null) { if (active) { votes.add(vote); } else { votes.remove(vote); } }
            else {
                try (var delete = connection.prepareStatement("DELETE FROM room_chat_reactions WHERE chat_id=? AND user_id=? AND emoji=?")) {
                    delete.setLong(1, chat); delete.setLong(2, actor); delete.setString(3, emoji); delete.executeUpdate();
                }
                if (active) { try (var insert = connection.prepareStatement("INSERT INTO room_chat_reactions(chat_id,user_id,emoji) VALUES(?,?,?)")) {
                    insert.setLong(1, chat); insert.setLong(2, actor); insert.setString(3, emoji); insert.executeUpdate();
                } }
            }
        });
    }

    public synchronized Map<Long, List<Reaction>> reactions(Collection<Long> chatIds, long viewer) {
        if (chatIds.size() > 200) { throw new IllegalArgumentException("Too many messages"); }
        Map<Long, List<Reaction>> result = new LinkedHashMap<>();
        for (long id : chatIds) { result.put(id, new ArrayList<>()); }
        if (chatIds.isEmpty()) { return result; }
        if (factory == null) {
            for (long id : chatIds) { for (String emoji : EMOJI) {
                int count = (int) votes.stream().filter(vote -> vote.chat() == id && vote.emoji().equals(emoji)).count();
                result.get(id).add(new Reaction(emoji, count, votes.contains(new Vote(id, viewer, emoji))));
            } }
            return result;
        }
        String placeholders = String.join(",", Collections.nCopies(chatIds.size(), "?"));
        try (var connection = factory.openDatabase(); var query = connection.prepareStatement("SELECT chat_id,emoji,COUNT(*),MAX(CASE WHEN user_id=? THEN 1 ELSE 0 END) FROM room_chat_reactions WHERE chat_id IN (" + placeholders + ") GROUP BY chat_id,emoji")) {
            query.setLong(1, viewer); int index = 2; for (long id : chatIds) { query.setLong(index++, id); }
            try (var rows = query.executeQuery()) { while (rows.next()) { result.get(rows.getLong(1)).add(new Reaction(rows.getString(2), rows.getInt(3), rows.getInt(4) == 1)); } }
            return result;
        } catch (SQLException exception) { throw failure(exception); }
    }

    private boolean mutate(long room, long actor, String nonce, String operation, Instant now, Write action) {
        if (!nonce.matches("[A-Za-z0-9_-]{8,64}")) { throw new IllegalArgumentException("Invalid interaction nonce"); }
        String signature = hash(operation);
        Receipt key = new Receipt(room, actor, nonce);
        if (factory == null) {
            Request previous = receipts.get(key);
            if (previous != null) { verify(previous.signature(), signature); return false; }
            Instant last = receipts.entrySet().stream().filter(entry -> entry.getKey().room() == room && entry.getKey().user() == actor)
                    .map(entry -> entry.getValue().time()).max(Comparator.naturalOrder()).orElse(null);
            if (last != null) { delay(last, now); }
            try { action.run(null); } catch (SQLException impossible) { throw failure(impossible); }
            State old = state(room); states.put(room, new State(old.revision() + 1, old.announcement(), old.updatedAt()));
            receipts.put(key, new Request(signature, now)); return true;
        }
        try (var connection = factory.openDatabase()) {
            connection.setAutoCommit(false);
            try {
                try (var lock = connection.prepareStatement("SELECT auction_id FROM auctions WHERE auction_id=? FOR UPDATE")) {
                    lock.setLong(1, room); try (var rows = lock.executeQuery()) { if (!rows.next()) { throw new SQLException("Unknown room"); } }
                }
                try (var query = connection.prepareStatement("SELECT signature FROM room_interaction_requests WHERE auction_id=? AND actor_id=? AND nonce=?")) {
                    query.setLong(1, room); query.setLong(2, actor); query.setString(3, nonce);
                    try (var rows = query.executeQuery()) { if (rows.next()) { verify(rows.getString(1), signature); connection.commit(); return false; } }
                }
                try (var query = connection.prepareStatement("SELECT MAX(created_at) FROM room_interaction_requests WHERE auction_id=? AND actor_id=?")) {
                    query.setLong(1, room); query.setLong(2, actor);
                    try (var rows = query.executeQuery()) { rows.next(); Timestamp last = rows.getTimestamp(1); if (last != null) { delay(last.toInstant(), now); } }
                }
                State old = state(connection, room);
                if (old.revision() == 0) { try (var insert = connection.prepareStatement("INSERT INTO room_interaction_state(auction_id,revision,announcement,updated_at) VALUES(?,0,'',?)")) {
                    insert.setLong(1, room); insert.setTimestamp(2, Timestamp.from(Instant.EPOCH)); insert.executeUpdate();
                } }
                action.run(connection);
                try (var update = connection.prepareStatement("UPDATE room_interaction_state SET revision=revision+1 WHERE auction_id=?")) {
                    update.setLong(1, room); update.executeUpdate();
                }
                try (var insert = connection.prepareStatement("INSERT INTO room_interaction_requests(auction_id,actor_id,nonce,signature,created_at) VALUES(?,?,?,?,?)")) {
                    insert.setLong(1, room); insert.setLong(2, actor); insert.setString(3, nonce); insert.setString(4, signature); insert.setTimestamp(5, Timestamp.from(now)); insert.executeUpdate();
                }
                connection.commit(); return true;
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw failure(exception); }
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private static void verify(String previous, String signature) {
        if (!previous.equals(signature)) { throw new IllegalArgumentException("Nonce already used for another interaction"); }
    }
    private static void delay(Instant last, Instant now) {
        if (now.toEpochMilli() - last.toEpochMilli() < 400) { throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Chờ một chút trước khi tương tác tiếp"); }
    }
    private static AuctionRepositoryException failure(SQLException exception) { return new AuctionRepositoryException("Room interaction storage failed", exception); }
}
