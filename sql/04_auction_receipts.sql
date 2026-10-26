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
