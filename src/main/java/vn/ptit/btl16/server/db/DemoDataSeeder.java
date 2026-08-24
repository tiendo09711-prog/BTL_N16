package vn.ptit.btl16.server.db;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.account.repository.UserRepository;
import vn.ptit.btl16.server.account.security.PasswordHasher;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Seeds accounts and short demo auctions so the team can run immediately. */
public final class DemoDataSeeder {
    private DemoDataSeeder() {
    }

    public static void seed(
            ServerConfig config,
            JdbcConnectionFactory factory,
            UserRepository users,
            PasswordHasher hasher) {
        seedUser(users, hasher, "demo", "Demo User", "demo123");
        seedUser(users, hasher, "alice", "Alice", "alice123");
        seedUser(users, hasher, "bob", "Bob", "bob123");
        seedProductsAndAuctions(config, factory);
    }

    private static void seedUser(
            UserRepository users,
            PasswordHasher hasher,
            String username,
            String displayName,
            String plainPassword) {
        if (users.existsByUsername(username)) {
            return;
        }
        char[] password = plainPassword.toCharArray();
        try {
            users.createUser(
                    username,
                    displayName,
                    username + "@local.test",
                    "",
                    hasher.hash(password));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static void seedProductsAndAuctions(
            ServerConfig config,
            JdbcConnectionFactory factory) {
        try (Connection connection = factory.openDatabase()) {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                long hostUserId = loadUserId(connection, "bob");
                upsertProduct(connection, hostUserId, "HEADSET", "Tai nghe gaming",
                        "Tai nghe khong day de demo realtime va anti-sniping.");
                upsertProduct(connection, hostUserId, "PHONE", "Dien thoai thong minh",
                        "San pham demo cho nhieu client cung theo doi.");
                upsertProduct(connection, hostUserId, "LAPTOP", "Laptop gaming",
                        "Phien dau gia dai hon de kiem thu room va reconnect.");

                if (!hasLiveAuction(connection)) {
                    Map<String, Long> productIds = loadProductIds(connection);
                    Instant now = Instant.now();
                    insertAuction(
                            connection,
                            hostUserId,
                            productIds.get("HEADSET"),
                            new BigDecimal("1000000.00"),
                            now.minusSeconds(5),
                            now.plusSeconds(config.getDemoShortAuctionSeconds()));
                    insertAuction(
                            connection,
                            hostUserId,
                            productIds.get("PHONE"),
                            new BigDecimal("5000000.00"),
                            now.minusSeconds(5),
                            now.plusSeconds(config.getDemoMediumAuctionSeconds()));
                    insertAuction(
                            connection,
                            hostUserId,
                            productIds.get("LAPTOP"),
                            new BigDecimal("10000000.00"),
                            now.minusSeconds(5),
                            now.plusSeconds(config.getDemoLongAuctionSeconds()));
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot seed demo data", exception);
        }
    }

    private static void upsertProduct(
            Connection connection,
            long ownerId,
            String code,
            String name,
            String description) throws SQLException {
        String sql = """
                INSERT INTO products(created_by, code, name, description, active)
                VALUES(?, ?, ?, ?, TRUE)
                ON DUPLICATE KEY UPDATE
                    created_by = VALUES(created_by),
                    name = VALUES(name),
                    description = VALUES(description),
                    active = TRUE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownerId);
            statement.setString(2, code);
            statement.setString(3, name);
            statement.setString(4, description);
            statement.executeUpdate();
        }
    }

    private static long loadUserId(Connection connection, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT user_id FROM users WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Demo host user was not found: " + username);
                }
                return resultSet.getLong("user_id");
            }
        }
    }

    private static boolean hasLiveAuction(Connection connection) throws SQLException {
        String sql = "SELECT 1 FROM auctions WHERE status = 'OPEN' AND end_time > CURRENT_TIMESTAMP(3) LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next();
        }
    }

    private static Map<String, Long> loadProductIds(Connection connection) throws SQLException {
        Map<String, Long> values = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT product_id, code FROM products WHERE code IN ('HEADSET','PHONE','LAPTOP')");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                values.put(resultSet.getString("code"), resultSet.getLong("product_id"));
            }
        }
        if (values.size() != 3) {
            throw new SQLException("Demo products could not be loaded");
        }
        return values;
    }

    private static void insertAuction(
            Connection connection,
            long hostUserId,
            long productId,
            BigDecimal startPrice,
            Instant startTime,
            Instant endTime) throws SQLException {
        String sql = """
                INSERT INTO auctions(
                    host_user_id, product_id, start_price, min_bid_increment,
                    current_price, current_winner_id,
                    start_time, end_time, status, ended_at, version)
                VALUES(?, ?, ?, 50000.00, ?, NULL, ?, ?, 'OPEN', NULL, 0)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, hostUserId);
            statement.setLong(2, productId);
            statement.setBigDecimal(3, startPrice);
            statement.setBigDecimal(4, startPrice);
            statement.setTimestamp(5, Timestamp.from(startTime));
            statement.setTimestamp(6, Timestamp.from(endTime));
            statement.executeUpdate();
        }
    }
}
