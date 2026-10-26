package vn.ptit.btl16.server.social;

import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import java.sql.SQLException;

public final class RoomSocialSchema {
    private RoomSocialSchema() { }

    public static void initialize(JdbcConnectionFactory factory) {
        try (var connection = factory.openDatabase(); var statement = connection.createStatement()) {
            boolean present;
            try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "auctions", "anonymous_guests")) {
                present = columns.next();
            }
            if (!present) { statement.executeUpdate("ALTER TABLE auctions ADD COLUMN anonymous_guests BOOLEAN NOT NULL DEFAULT FALSE"); }
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS room_participants (
                        auction_id BIGINT NOT NULL, user_id BIGINT NOT NULL, ordinal_no BIGINT NOT NULL,
                        PRIMARY KEY (auction_id, user_id), UNIQUE (auction_id, ordinal_no),
                        FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
                        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS room_chat (
                        chat_id BIGINT AUTO_INCREMENT PRIMARY KEY, auction_id BIGINT NOT NULL,
                        sender_id BIGINT NOT NULL, client_nonce VARCHAR(64) NOT NULL,
                        body VARCHAR(2000) NOT NULL, sent_at DATETIME(3) NOT NULL,
                        UNIQUE (auction_id, sender_id, client_nonce),
                        INDEX idx_room_chat_history (auction_id, chat_id),
                        FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
                        FOREIGN KEY (sender_id) REFERENCES users(user_id) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS user_public_profiles (
                        user_id BIGINT PRIMARY KEY, biography VARCHAR(2000) NOT NULL,
                        avatar_base64 TEXT NOT NULL, profile_version BIGINT NOT NULL,
                        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                    )
                    """);
            try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "room_chat", "reply_to_id")) {
                if (!columns.next()) { statement.executeUpdate("ALTER TABLE room_chat ADD COLUMN reply_to_id BIGINT NOT NULL DEFAULT 0"); }
            }
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS room_interaction_state (
                        auction_id BIGINT PRIMARY KEY, revision BIGINT NOT NULL,
                        announcement VARCHAR(2000) NOT NULL, updated_at DATETIME(3) NOT NULL,
                        FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS room_chat_mutes (
                        auction_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
                        reason VARCHAR(1000) NOT NULL, until_at DATETIME(3) NOT NULL,
                        PRIMARY KEY (auction_id,user_id),
                        FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
                        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS room_chat_reactions (
                        chat_id BIGINT NOT NULL, user_id BIGINT NOT NULL, emoji VARCHAR(16) NOT NULL,
                        PRIMARY KEY (chat_id,user_id,emoji),
                        FOREIGN KEY (chat_id) REFERENCES room_chat(chat_id) ON DELETE CASCADE,
                        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS room_interaction_requests (
                        auction_id BIGINT NOT NULL, actor_id BIGINT NOT NULL, nonce VARCHAR(64) NOT NULL,
                        signature VARCHAR(64) NOT NULL, created_at DATETIME(3) NOT NULL,
                        PRIMARY KEY (auction_id,actor_id,nonce), INDEX idx_interaction_rate (auction_id,actor_id,created_at),
                        FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
                        FOREIGN KEY (actor_id) REFERENCES users(user_id) ON DELETE CASCADE
                    )
                    """);
        } catch (SQLException exception) { throw new IllegalStateException("Cannot initialize room social schema", exception); }
    }
}
