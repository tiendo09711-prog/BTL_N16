-- BTL 16 - Realtime Auction
-- Run with Laragon Database/HeidiSQL when not using DatabaseSetupMain.

CREATE DATABASE IF NOT EXISTS `btl_16`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `btl_16`;

CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(30) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    email VARCHAR(120) NULL,
    phone VARCHAR(20) NULL,
    password_hash VARCHAR(255) NOT NULL,
    password_salt VARCHAR(255) NOT NULL,
    password_iterations INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NULL,
    INDEX idx_users_active(active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS login_history (
    login_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NULL,
    username_attempt VARCHAR(30) NOT NULL,
    remote_address VARCHAR(120) NOT NULL,
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(80) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_login_history_user(user_id),
    INDEX idx_login_history_created(created_at),
    CONSTRAINT fk_login_history_user
        FOREIGN KEY(user_id) REFERENCES users(user_id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS products (
    product_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    created_by BIGINT NOT NULL,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT NULL,
    image_data MEDIUMBLOB NULL,
    image_mime VARCHAR(50) NULL,
    image_name VARCHAR(255) NULL,
    image_size INT NULL,
    image_version BIGINT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_products_owner_active(created_by, active),
    CONSTRAINT fk_products_owner
        FOREIGN KEY(created_by) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product_media (
    media_id VARCHAR(36) PRIMARY KEY,
    product_id BIGINT NOT NULL,
    kind VARCHAR(8) NOT NULL,
    original_name VARCHAR(150) NOT NULL,
    source_type VARCHAR(80) NOT NULL,
    stored_bytes BIGINT NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    duration_seconds DOUBLE NOT NULL,
    sort_order INT NOT NULL,
    INDEX idx_product_media_order(product_id, sort_order),
    CONSTRAINT fk_product_media_product FOREIGN KEY(product_id) REFERENCES products(product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS auctions (
    room_code VARCHAR(6) NULL,
    UNIQUE KEY uq_auction_room_code (room_code),
    auction_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    host_user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    start_price DECIMAL(18,2) NOT NULL,
    min_bid_increment DECIMAL(18,2) NOT NULL DEFAULT 0.01,
    current_price DECIMAL(18,2) NOT NULL,
    current_winner_id BIGINT NULL,
    start_time DATETIME(3) NOT NULL,
    end_time DATETIME(3) NOT NULL,
    hard_end_time DATETIME(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    ended_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    anonymous_guests BOOLEAN NOT NULL DEFAULT FALSE,
    max_participants INT NOT NULL DEFAULT 100,
    deposit_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    room_password_hash VARCHAR(255) NULL,
    room_password_salt VARCHAR(255) NULL,
    room_password_iterations INT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_auctions_status_end(status, end_time),
    INDEX idx_auctions_host(host_user_id),
    CONSTRAINT fk_auctions_host
        FOREIGN KEY(host_user_id) REFERENCES users(user_id),
    CONSTRAINT fk_auctions_product
        FOREIGN KEY(product_id) REFERENCES products(product_id),
    CONSTRAINT fk_auctions_winner
        FOREIGN KEY(current_winner_id) REFERENCES users(user_id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bids (
    bid_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    auction_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    server_sequence BIGINT NOT NULL UNIQUE,
    created_at DATETIME(3) NOT NULL,
    INDEX idx_bids_auction_sequence(auction_id, server_sequence),
    INDEX idx_bids_user(user_id),
    CONSTRAINT fk_bids_auction
        FOREIGN KEY(auction_id) REFERENCES auctions(auction_id),
    CONSTRAINT fk_bids_user
        FOREIGN KEY(user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS auction_results (
    auction_id BIGINT PRIMARY KEY,
    winner_id BIGINT NULL,
    final_price DECIMAL(18,2) NOT NULL,
    ended_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_results_auction
        FOREIGN KEY(auction_id) REFERENCES auctions(auction_id),
    CONSTRAINT fk_results_winner
        FOREIGN KEY(winner_id) REFERENCES users(user_id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS auction_blocked_users (
    auction_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    blocked_by BIGINT NOT NULL,
    blocked_at DATETIME(3) NOT NULL,
    PRIMARY KEY(auction_id, user_id),
    CONSTRAINT fk_blocked_auction
        FOREIGN KEY(auction_id) REFERENCES auctions(auction_id),
    CONSTRAINT fk_blocked_user
        FOREIGN KEY(user_id) REFERENCES users(user_id),
    CONSTRAINT fk_blocked_by
        FOREIGN KEY(blocked_by) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS media_content (
    media_id VARCHAR(36) NOT NULL,
    variant VARCHAR(32) NOT NULL,
    chunk_no INT NOT NULL,
    payload LONGBLOB NOT NULL,
    PRIMARY KEY(media_id, variant, chunk_no),
    FOREIGN KEY(media_id) REFERENCES product_media(media_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS room_participants (
    auction_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    ordinal_no BIGINT NOT NULL,
    PRIMARY KEY (auction_id, user_id),
    UNIQUE (auction_id, ordinal_no),
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS room_chat (
    chat_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    auction_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    client_nonce VARCHAR(64) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    sent_at DATETIME(3) NOT NULL,
    reply_to_id BIGINT NOT NULL DEFAULT 0,
    UNIQUE (auction_id, sender_id, client_nonce),
    INDEX idx_room_chat_history (auction_id, chat_id),
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_public_profiles (
    user_id BIGINT PRIMARY KEY,
    biography VARCHAR(2000) NOT NULL,
    avatar_base64 TEXT NOT NULL,
    profile_version BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

CREATE TABLE IF NOT EXISTS auction_receipt_summaries (
    auction_id BIGINT PRIMARY KEY,
    product_code VARCHAR(50) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    ended_at DATETIME(3) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    winner_display VARCHAR(100) NOT NULL,
    final_price DECIMAL(18,2) NULL,
    total_bids BIGINT NOT NULL,
    bidder_count BIGINT NOT NULL,
    extension_count BIGINT NOT NULL,
    extensions_complete BOOLEAN NOT NULL,
    anonymous_guests BOOLEAN NOT NULL,
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS auction_personal_receipts (
    auction_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    personal_role VARCHAR(16) NOT NULL,
    PRIMARY KEY (auction_id, user_id),
    INDEX idx_receipt_owner (user_id, auction_id),
    FOREIGN KEY (auction_id) REFERENCES auction_receipt_summaries(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS personal_notifications (
    notification_id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
    source_key VARCHAR(160) NOT NULL UNIQUE, kind VARCHAR(32) NOT NULL,
    auction_id BIGINT NULL, reference_id VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL, read_at DATETIME(3) NULL,
    INDEX idx_notification_owner (user_id, notification_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
