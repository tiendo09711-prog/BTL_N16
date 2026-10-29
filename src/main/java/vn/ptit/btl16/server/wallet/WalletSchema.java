package vn.ptit.btl16.server.wallet;

import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class WalletSchema {
    private WalletSchema() { }

    public static void initialize(JdbcConnectionFactory factory) {
        try (Connection connection = factory.openDatabase(); Statement statement = connection.createStatement()) {
            column(connection, statement, "users", "wallet_balance", "DECIMAL(18,2) NOT NULL DEFAULT 0");
            column(connection, statement, "users", "wallet_reserved", "DECIMAL(18,2) NOT NULL DEFAULT 0");
            column(connection, statement, "users", "vault_balance", "DECIMAL(18,2) NOT NULL DEFAULT 0");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS wallet_vaults (
                        user_id BIGINT PRIMARY KEY, pin_salt VARCHAR(64) NOT NULL,
                        pin_hash VARCHAR(128) NOT NULL, pin_iterations INT NOT NULL,
                        failed_attempts INT NOT NULL DEFAULT 0, locked_until DATETIME(3) NULL,
                        FOREIGN KEY(user_id) REFERENCES users(user_id)
                    ) ENGINE=InnoDB
                    """);
            column(connection, statement, "auctions", "wallet_required", "BOOLEAN NOT NULL DEFAULT FALSE");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS wallet_topups (
                        request_id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL,
                        amount DECIMAL(18,2) NOT NULL, status VARCHAR(16) NOT NULL,
                        created_at DATETIME(3) NOT NULL, reviewed_at DATETIME(3) NULL,
                        FOREIGN KEY(user_id) REFERENCES users(user_id),
                        INDEX idx_topups_user(user_id, created_at), INDEX idx_topups_pending(status, created_at)
                    ) ENGINE=InnoDB
                    """);
            column(connection, statement, "wallet_topups", "rejection_reason", "VARCHAR(300) NOT NULL DEFAULT ''");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS wallet_withdrawals (
                        request_id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL,
                        amount DECIMAL(18,2) NOT NULL, status VARCHAR(16) NOT NULL,
                        created_at DATETIME(3) NOT NULL, reviewed_at DATETIME(3) NULL,
                        rejection_reason VARCHAR(300) NOT NULL DEFAULT '',
                        FOREIGN KEY(user_id) REFERENCES users(user_id),
                        INDEX idx_withdrawals_user(user_id, created_at), INDEX idx_withdrawals_pending(status, created_at)
                    ) ENGINE=InnoDB
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS wallet_holds (
                        auction_id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, amount DECIMAL(18,2) NOT NULL,
                        FOREIGN KEY(auction_id) REFERENCES auctions(auction_id),
                        FOREIGN KEY(user_id) REFERENCES users(user_id)
                    ) ENGINE=InnoDB
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS wallet_entries (
                        entry_id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL,
                        entry_type VARCHAR(32) NOT NULL, amount DECIMAL(18,2) NOT NULL,
                        reserved_delta DECIMAL(18,2) NOT NULL, balance_after DECIMAL(18,2) NOT NULL,
                        reserved_after DECIMAL(18,2) NOT NULL, reference_key VARCHAR(120) NOT NULL UNIQUE,
                        created_at DATETIME(3) NOT NULL, FOREIGN KEY(user_id) REFERENCES users(user_id),
                        INDEX idx_wallet_entries_user(user_id, entry_id)
                    ) ENGINE=InnoDB
                    """);
            column(connection, statement, "wallet_entries", "vault_after", "DECIMAL(18,2) NOT NULL DEFAULT 0");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS wallet_settlements (
                        auction_id BIGINT PRIMARY KEY, buyer_id BIGINT NOT NULL, seller_id BIGINT NOT NULL,
                        amount DECIMAL(18,2) NOT NULL, status VARCHAR(24) NOT NULL, created_at DATETIME(3) NOT NULL,
                        FOREIGN KEY(auction_id) REFERENCES auctions(auction_id),
                        FOREIGN KEY(buyer_id) REFERENCES users(user_id), FOREIGN KEY(seller_id) REFERENCES users(user_id)
                    ) ENGINE=InnoDB
                    """);
            column(connection, statement, "wallet_settlements", "fee_rate", "DECIMAL(5,4) NOT NULL DEFAULT 0");
            column(connection, statement, "wallet_settlements", "platform_fee", "DECIMAL(18,2) NOT NULL DEFAULT 0");
            column(connection, statement, "wallet_settlements", "base_fee_rate", "DECIMAL(5,4) NOT NULL DEFAULT 0");
            column(connection, statement, "wallet_settlements", "fee_discount_rate", "DECIMAL(5,4) NOT NULL DEFAULT 0");
            column(connection, statement, "wallet_settlements", "revenue_rank", "INT NOT NULL DEFAULT 0");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS revenue_settlement_lock (lock_id INT PRIMARY KEY) ENGINE=InnoDB");
            statement.executeUpdate("INSERT INTO revenue_settlement_lock(lock_id) VALUES(1) ON DUPLICATE KEY UPDATE lock_id=1");
        } catch (SQLException exception) { throw new IllegalStateException("Cannot upgrade wallet schema", exception); }
    }

    private static void column(Connection connection, Statement statement, String table, String name, String type) throws SQLException {
        for (String candidate : new String[]{table, table.toUpperCase(java.util.Locale.ROOT)}) {
            try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, candidate, null)) {
                while (columns.next()) { if (columns.getString("COLUMN_NAME").equalsIgnoreCase(name)) { return; } }
            }
        }
        statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + name + " " + type);
    }
}
