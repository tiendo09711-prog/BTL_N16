package vn.ptit.btl16.server.auction.repository;

import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import java.sql.SQLException;

/** Irreversible catalog deletion marker for products retained solely by auction history. */
public final class ProductDeletionStore {
    private ProductDeletionStore() { }
    public static void initialize(JdbcConnectionFactory factory) {
        try (var connection = factory.openDatabase(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS deleted_products (
                        product_id BIGINT PRIMARY KEY, deleted_at DATETIME(3) NOT NULL,
                        FOREIGN KEY(product_id) REFERENCES products(product_id) ON DELETE CASCADE
                    ) ENGINE=InnoDB
                    """);
        } catch (SQLException error) { throw new AuctionRepositoryException("Cannot initialize product deletions", error); }
    }
}
