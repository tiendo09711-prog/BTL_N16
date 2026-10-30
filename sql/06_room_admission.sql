USE `btl_16`;

CREATE TABLE IF NOT EXISTS auction_deposits (
    auction_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    released_at TIMESTAMP(3) NULL,
    PRIMARY KEY (auction_id, user_id),
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
