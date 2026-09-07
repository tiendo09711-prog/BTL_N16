package vn.ptit.btl16.selftest;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.db.DatabaseResetMain;
import vn.ptit.btl16.server.db.DatabaseSchema;
import vn.ptit.btl16.server.db.DatabaseSetupMain;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;

public final class XamppDatabaseSelfTest {
    private static final String[] TABLES = {
            "users", "login_history", "products", "auctions", "bids",
            "auction_results", "auction_blocked_users"
    };

    private XamppDatabaseSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        String previous = System.getProperty("btl16.server.config");
        Properties properties = new Properties();
        Path source = Path.of(previous == null ? "config/server.properties" : previous);
        try (Reader reader = Files.newBufferedReader(source)) {
            properties.load(reader);
        }
        String database = "btl16_verify_" + UUID.randomUUID().toString().replace("-", "");
        properties.setProperty("db.name", database);
        properties.setProperty("db.autoInitialize", "true");
        Path configFile = Files.createTempFile("btl16-xampp-test-", ".properties");
        JdbcConnectionFactory factory = null;
        try {
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                properties.store(writer, null);
            }
            System.setProperty("btl16.server.config", configFile.toString());
            ServerConfig config = ServerConfig.loadDefault();
            TestSupport.equals(database, config.getDbName(), "test must use its own database");
            factory = new JdbcConnectionFactory(config);
            DatabaseSetupMain.main(new String[0]);
            assertEmpty(factory);
            try (ServerApplication server = ServerApplication.create(config)) {
                assertEmpty(factory);
            }
            try (Connection connection = factory.openDatabase(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO users(username, display_name, password_hash, "
                        + "password_salt, password_iterations, active) "
                        + "VALUES('test_only', 'Test Only', 'not-a-login-hash', 'test-salt', 120000, TRUE)");
            }
            DatabaseSetupMain.main(new String[0]);
            try (Connection connection = factory.openDatabase(); Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM users")) {
                result.next();
                TestSupport.equals(1L, result.getLong(1), "setup preserves existing rows without seeding");
            }
            DatabaseResetMain.main(new String[0]);
            assertEmpty(factory);
            System.out.println("[PASS] XamppDatabaseSelfTest: empty setup/startup, non-destructive setup, empty reset");
        } finally {
            try {
                if (factory != null && database.equals(factory.getConfig().getDbName())) {
                    DatabaseSchema.dropDatabase(factory);
                }
            } finally {
                if (previous == null) {
                    System.clearProperty("btl16.server.config");
                } else {
                    System.setProperty("btl16.server.config", previous);
                }
                Files.deleteIfExists(configFile);
            }
        }
    }

    private static void assertEmpty(JdbcConnectionFactory factory) throws Exception {
        try (Connection connection = factory.openDatabase(); Statement statement = connection.createStatement()) {
            for (String table : TABLES) {
                try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    result.next();
                    TestSupport.equals(0L, result.getLong(1), table + " must remain empty");
                }
            }
        }
    }
}
