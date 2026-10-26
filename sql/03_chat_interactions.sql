-- Existing databases: add reply_to_id only if missing. Automatic startup migration
-- performs this metadata check; do not run the ALTER twice manually.
ALTER TABLE room_chat ADD COLUMN reply_to_id BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS room_interaction_state (
    auction_id BIGINT PRIMARY KEY, revision BIGINT NOT NULL,
    announcement VARCHAR(2000) NOT NULL, updated_at DATETIME(3) NOT NULL,
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS room_chat_mutes (
    auction_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
    reason VARCHAR(1000) NOT NULL, until_at DATETIME(3) NOT NULL,
    PRIMARY KEY (auction_id,user_id),
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS room_chat_reactions (
    chat_id BIGINT NOT NULL, user_id BIGINT NOT NULL, emoji VARCHAR(16) NOT NULL,
    PRIMARY KEY (chat_id,user_id,emoji),
    FOREIGN KEY (chat_id) REFERENCES room_chat(chat_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS room_interaction_requests (
    auction_id BIGINT NOT NULL, actor_id BIGINT NOT NULL, nonce VARCHAR(64) NOT NULL,
    signature VARCHAR(64) NOT NULL, created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (auction_id,actor_id,nonce), INDEX idx_interaction_rate (auction_id,actor_id,created_at),
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (actor_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
