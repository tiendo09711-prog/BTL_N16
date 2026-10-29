package vn.ptit.btl16.server.auction.repository;

import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;

import java.sql.Connection;
import java.sql.SQLException;

/** Global product numbers, allocated under a database lock in the product transaction. */
public final class ProductCodeStore {
    private ProductCodeStore() { }

    public static void initialize(JdbcConnectionFactory factory) {
        try (var connection = factory.openDatabase(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS product_code_sequence (
                        sequence_id INT PRIMARY KEY, next_number BIGINT NOT NULL
                    ) ENGINE=InnoDB
                    """);
            statement.executeUpdate("""
                    INSERT INTO product_code_sequence(sequence_id, next_number) VALUES(1, 1)
                    ON DUPLICATE KEY UPDATE sequence_id=1
                    """);
        } catch (SQLException error) {
            throw new AuctionRepositoryException("Cannot initialize product codes", error);
        }
    }

    static String allocate(Connection connection) throws SQLException {
        if (connection.getAutoCommit()) { throw new SQLException("Product code allocation requires a transaction"); }
        long number;
        try (var query = connection.prepareStatement(
                "SELECT next_number FROM product_code_sequence WHERE sequence_id=1 FOR UPDATE");
             var rows = query.executeQuery()) {
            if (!rows.next()) { throw new SQLException("Product code sequence is missing"); }
            number = rows.getLong(1);
        }
        // Existing manually assigned codes remain valid, including codes already using MH.
        try (var query = connection.prepareStatement("SELECT 1 FROM products WHERE code=?")) {
            while (true) {
                if (number < 1 || number == Long.MAX_VALUE) { throw new SQLException("Product code sequence exhausted"); }
                String code = "MH" + number++;
                query.setString(1, code);
                try (var rows = query.executeQuery()) { if (rows.next()) { continue; } }
                try (var update = connection.prepareStatement(
                        "UPDATE product_code_sequence SET next_number=? WHERE sequence_id=1")) {
                    update.setLong(1, number);
                    update.executeUpdate();
                }
                return code;
            }
        }
    }
}
