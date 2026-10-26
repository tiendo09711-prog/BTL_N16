CREATE TABLE IF NOT EXISTS personal_notifications (
    notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    source_key VARCHAR(160) NOT NULL UNIQUE,
    kind VARCHAR(32) NOT NULL,
    auction_id BIGINT NULL,
    reference_id VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    read_at DATETIME(3) NULL,
    INDEX idx_notification_owner (user_id, notification_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Existing installations: DatabaseSchema.initialize also adds wallet_topups.rejection_reason
-- VARCHAR(300) NOT NULL DEFAULT '' only when absent. No old requests/ledger are rewritten.
-- Do not run reset scripts. This migration does not backfill historical notifications.
-- With db.autoInitialize=false, use the existing DatabaseSetupMain once with the
-- intended MySQL config to apply the column and table upgrades idempotently.
