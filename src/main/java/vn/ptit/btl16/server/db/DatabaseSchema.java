package vn.ptit.btl16.server.db;

import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates the small MySQL schema used by the network-programming project. */
public final class DatabaseSchema {
    private DatabaseSchema() {
    }

    public static void initialize(JdbcConnectionFactory factory) {
        createDatabase(factory);
        createTables(factory);
        upgradeAuctionManagementSchema(factory);
    }

    public static void dropDatabase(JdbcConnectionFactory factory) {
        String name = factory.getConfig().getDbName();
        try (Connection connection = factory.openMysqlServer();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP DATABASE IF EXISTS `" + name + "`");
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot drop database " + name, exception);
        }
    }

    private static void createDatabase(JdbcConnectionFactory factory) {
        String name = factory.getConfig().getDbName();
        String sql = "CREATE DATABASE IF NOT EXISTS `" + name
                + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
        try (Connection connection = factory.openMysqlServer();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot create database " + name, exception);
        }
    }

    private static void createTables(JdbcConnectionFactory factory) {
        String users = """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;

        String loginHistory = """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;

        String products = """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;

        String auctions = """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;

        String bids = """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;

        String results = """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;

        String blockedUsers = """
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;

        try (Connection connection = factory.openDatabase();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(users);
            statement.executeUpdate(loginHistory);
            statement.executeUpdate(products);
            statement.executeUpdate(auctions);
            statement.executeUpdate(bids);
            statement.executeUpdate(results);
            statement.executeUpdate(blockedUsers);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot create database tables", exception);
        }
    }

    private static void upgradeAuctionManagementSchema(JdbcConnectionFactory factory) {
        try (Connection connection = factory.openDatabase();
             Statement statement = connection.createStatement()) {
            addColumnIfMissing(connection, statement, "products", "created_by", "BIGINT NULL");
            addColumnIfMissing(connection, statement, "products", "active", "BOOLEAN NOT NULL DEFAULT TRUE");
            addColumnIfMissing(
                    connection,
                    statement,
                    "products",
                    "updated_at",
                    "DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)");
            addColumnIfMissing(connection, statement, "products", "image_data", "MEDIUMBLOB NULL");
            addColumnIfMissing(connection, statement, "products", "image_mime", "VARCHAR(50) NULL");
            addColumnIfMissing(connection, statement, "products", "image_name", "VARCHAR(255) NULL");
            addColumnIfMissing(connection, statement, "products", "image_size", "INT NULL");
            addColumnIfMissing(
                    connection, statement, "products", "image_version", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "auctions", "host_user_id", "BIGINT NULL");
            addColumnIfMissing(
                    connection,
                    statement,
                    "auctions",
                    "min_bid_increment",
                    "DECIMAL(18,2) NOT NULL DEFAULT 0.01");
            addColumnIfMissing(
                    connection, statement, "auctions", "visibility",
                    "VARCHAR(16) NOT NULL DEFAULT 'PUBLIC'");
            addColumnIfMissing(
                    connection, statement, "auctions", "room_password_hash", "VARCHAR(255) NULL");
            addColumnIfMissing(
                    connection, statement, "auctions", "room_password_salt", "VARCHAR(255) NULL");
            addColumnIfMissing(
                    connection, statement, "auctions", "room_password_iterations", "INT NULL");
            statement.executeUpdate("""
                    UPDATE products
                    SET created_by = (SELECT user_id FROM users ORDER BY user_id LIMIT 1)
                    WHERE created_by IS NULL AND EXISTS (SELECT 1 FROM users)
                    """);
            statement.executeUpdate("""
                    UPDATE auctions a
                    JOIN products p ON p.product_id = a.product_id
                    SET a.host_user_id = p.created_by
                    WHERE a.host_user_id IS NULL
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS auction_blocked_users (
                        auction_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        blocked_by BIGINT NOT NULL,
                        blocked_at DATETIME(3) NOT NULL,
                        PRIMARY KEY(auction_id, user_id),
                        CONSTRAINT fk_blocked_auction FOREIGN KEY(auction_id) REFERENCES auctions(auction_id),
                        CONSTRAINT fk_blocked_user FOREIGN KEY(user_id) REFERENCES users(user_id),
                        CONSTRAINT fk_blocked_by FOREIGN KEY(blocked_by) REFERENCES users(user_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot upgrade auction management schema", exception);
        }
    }

    private static void addColumnIfMissing(
            Connection connection,
            Statement statement,
            String table,
            String column,
            String definition) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(
                connection.getCatalog(), null, table, column)) {
            if (!columns.next()) {
                statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + ' ' + definition);
            }
        }
    }
}
