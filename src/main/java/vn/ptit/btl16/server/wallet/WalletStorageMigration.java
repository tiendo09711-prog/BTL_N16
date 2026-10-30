package vn.ptit.btl16.server.wallet;

import java.sql.Connection;
import java.sql.SQLException;

final class WalletStorageMigration {
    private WalletStorageMigration() { }

    static void apply(Connection connection) throws SQLException {
        boolean storedBalance = hasColumn(connection, "users", "vault_balance");
        boolean storedHistory = hasColumn(connection, "wallet_entries", "vault_after");
        connection.setAutoCommit(false);
        try (var statement = connection.createStatement()) {
            if (storedBalance) {
                try (var owners = statement.executeQuery("SELECT user_id FROM users ORDER BY user_id FOR UPDATE")) {
                    while (owners.next()) { owners.getLong("user_id"); }
                }
                try (var invalid = statement.executeQuery("""
                        SELECT user_id FROM users WHERE vault_balance < 0
                            OR wallet_balance + vault_balance > 9999999999999999.99
                        """)) {
                    if (invalid.next()) { throw new SQLException("Cannot consolidate legacy wallet funds for user " + invalid.getLong("user_id")); }
                }
            }
            statement.executeUpdate("DELETE FROM wallet_entries WHERE entry_type IN ('VAULT_DEPOSIT','VAULT_WITHDRAWAL') OR reference_key LIKE 'VAULT:%'");
            if (storedHistory) {
                statement.executeUpdate("UPDATE wallet_entries SET balance_after=balance_after+vault_after, vault_after=0 WHERE vault_after<>0");
            }
            if (storedBalance) {
                statement.executeUpdate("UPDATE users SET wallet_balance=wallet_balance+vault_balance, vault_balance=0 WHERE vault_balance<>0");
            }
            connection.commit();
        } catch (SQLException | RuntimeException error) {
            connection.rollback(); throw error;
        } finally { connection.setAutoCommit(true); }
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE IF EXISTS wallet_vaults");
            if (storedHistory) { statement.executeUpdate("ALTER TABLE wallet_entries DROP COLUMN vault_after"); }
            if (storedBalance) { statement.executeUpdate("ALTER TABLE users DROP COLUMN vault_balance"); }
        }
    }

    private static boolean hasColumn(Connection connection, String table, String name) throws SQLException {
        try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, null, null)) {
            while (columns.next()) {
                if (table.equalsIgnoreCase(columns.getString("TABLE_NAME")) && name.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) { return true; }
            }
        }
        return false;
    }
}
