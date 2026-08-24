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

CREATE TABLE IF NOT EXISTS auctions (
    auction_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    host_user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    start_price DECIMAL(18,2) NOT NULL,
    min_bid_increment DECIMAL(18,2) NOT NULL DEFAULT 0.01,
    current_price DECIMAL(18,2) NOT NULL,
    current_winner_id BIGINT NULL,
    start_time DATETIME(3) NOT NULL,
    end_time DATETIME(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    ended_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
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
