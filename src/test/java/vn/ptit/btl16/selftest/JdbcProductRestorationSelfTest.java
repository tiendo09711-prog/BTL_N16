package vn.ptit.btl16.selftest;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.Product;
import vn.ptit.btl16.server.auction.repository.JdbcAuctionRepository;
import vn.ptit.btl16.server.db.DatabaseSchema;

import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public final class JdbcProductRestorationSelfTest {
    private JdbcProductRestorationSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 || (args.length == 1 && !args[0].equals("--mysql"))) {
            throw new IllegalArgumentException("Use no arguments for isolated H2, or --mysql for an isolated MySQL database");
        }
        runIsolated(args.length == 1);
    }

    public static void run() throws Exception {
        runIsolated(false);
    }

    private static void runIsolated(boolean mysql) throws Exception {
        String previousConfig = System.getProperty("btl16.server.config");
        String previousDirectory = System.getProperty("btl16.dataDir");
        String database = "btl16_verify_restore_" + UUID.randomUUID().toString().replace("-", "");
        Path directory = Files.createTempDirectory("btl16-restore-jdbc-").toRealPath();
        Properties properties = new Properties();
        JdbcConnectionFactory factory = null;
        try {
            if (mysql) {
                Path source = Path.of(previousConfig == null ? "config/server.properties" : previousConfig);
                try (Reader reader = Files.newBufferedReader(source)) {
                    properties.load(reader);
                }
                TestSupport.equals("com.mysql.cj.jdbc.Driver", properties.getProperty("db.driver"),
                        "MySQL tests require the MySQL driver");
            } else {
                properties.setProperty("db.driver", "org.h2.Driver");
                properties.setProperty("db.user", "sa");
                properties.setProperty("db.password", "");
            }
            properties.setProperty("db.name", database);
            properties.setProperty("db.autoInitialize", "false");
            Path configFile = directory.resolve("server.properties");
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                properties.store(writer, null);
            }
            System.setProperty("btl16.server.config", configFile.toString());
            System.setProperty("btl16.dataDir", directory.toString());
            ServerConfig config = ServerConfig.loadDefault();
            TestSupport.equals(database, config.getDbName(), "only the generated isolated database may be initialized");
            TestSupport.check(config.isEmbeddedDatabase() != mysql, "database driver matches the test mode");
            factory = new JdbcConnectionFactory(config);
            requireIsolated(factory);
            if (!mysql) {
                TestSupport.check(config.databaseJdbcUrl().startsWith("jdbc:h2:file:"
                        + directory.toString().replace('\\', '/') + "/"), "H2 files stay inside the temporary test directory");
            }
            DatabaseSchema.initialize(factory);
            try (Connection connection = factory.openDatabase(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO users(user_id, username, display_name, password_hash, password_salt, password_iterations, active) "
                        + "VALUES(1, 'host', 'Host', 'test', 'test', 30000, TRUE), "
                        + "(2, 'buyer', 'Buyer', 'test', 'test', 30000, TRUE), "
                        + "(3, 'viewer', 'Viewer', 'test', 'test', 30000, TRUE)");
            }
            verify(factory);
            System.out.println("[PASS] JdbcProductRestorationSelfTest (" + (mysql ? "MySQL/MariaDB" : "H2")
                    + "): isolated transactions, unchanged historical rows, persistent active flag, idempotence and multi-connection races");
        } finally {
            try {
                if (mysql && factory != null && database.equals(factory.getConfig().getDbName())) {
                    requireIsolated(factory);
                    DatabaseSchema.dropDatabase(factory);
                }
            } finally {
                restoreProperty("btl16.server.config", previousConfig);
                restoreProperty("btl16.dataDir", previousDirectory);
                try (var paths = Files.walk(directory)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                        TestSupport.check(path.toAbsolutePath().normalize().startsWith(directory), "cleanup stays in the test directory");
                        Files.deleteIfExists(path);
                    }
                }
            }
        }
    }

    static void verify(JdbcConnectionFactory factory) throws Exception {
        requireIsolated(factory);
        TestMedia.fundWallets(new vn.ptit.btl16.server.wallet.WalletRepository(factory));
        JdbcAuctionRepository repository = new JdbcAuctionRepository(factory);
        ProductRestorationSelfTest.verify(repository, productId -> {
            try (Connection connection = factory.openDatabase(); PreparedStatement statement = connection.prepareStatement(
                    "UPDATE products SET active = FALSE WHERE product_id = ?")) {
                statement.setLong(1, productId);
                TestSupport.equals(1, statement.executeUpdate(), "inject an inactive/open legacy state only in the isolated test DB");
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot arrange isolated test data", exception);
            }
        });
        unchangedHistoricalRows(factory, repository);
        ProductRestorationSelfTest.concurrency(repository, new JdbcAuctionRepository(factory));
    }

    private static void unchangedHistoricalRows(JdbcConnectionFactory factory, JdbcAuctionRepository repository) throws Exception {
        AuctionRulesSelfTest.Fixture fixture = new AuctionRulesSelfTest.Fixture(repository);
        byte[] image;
        try (var stream = JdbcProductRestorationSelfTest.class.getResourceAsStream("/ui/auction.png")) {
            image = stream.readAllBytes();
        }
        Product product = TestMedia.create(fixture.management, fixture.host, "RESTORE_DB_HISTORY", "History item", "Keep content",
                image, "image/png", "history.png");
        AuctionSnapshot old = fixture.management.createAuction(fixture.host, product.getProductId(),
                new BigDecimal("100"), new BigDecimal("10"), 1);
        fixture.management.joinAuction(fixture.viewer, "viewer", old.getAuctionId());
        fixture.management.kickUser(fixture.host, old.getAuctionId(), "viewer");
        fixture.management.extendAuction(fixture.host, old.getAuctionId(), 60);
        fixture.management.endAuction(fixture.host, old.getAuctionId());
        fixture.management.deactivateProduct(fixture.host, product.getProductId());
        Map<String, List<List<String>>> before = history(factory);
        TestSupport.check(!before.get("bids").isEmpty() && !before.get("auction_results").isEmpty()
                && !before.get("auction_blocked_users").isEmpty(), "history assertions include bids, results and bans");
        fixture.management.restoreProduct(fixture.host, product.getProductId());
        TestSupport.equals(before, history(factory), "successful restore updates no historical SQL row or auction limit");
        TestSupport.check(new JdbcAuctionRepository(factory).findProductById(product.getProductId()).orElseThrow().isActive(),
                "restored state persists through a new JDBC repository/connection");
        List<List<String>> productRows = rows(factory, "products", "product_id");
        fixture.management.restoreProduct(fixture.host, product.getProductId());
        repository.restoreProduct(product.getProductId(), 1L);
        ProductRestorationSelfTest.expect(ErrorCode.AUCTION_FORBIDDEN, () -> repository.restoreProduct(product.getProductId(), 2L));
        Product sold = repository.findProductsByOwner(1L).stream()
                .filter(value -> repository.hasSoldAuctionForProduct(value.getProductId())).findFirst().orElseThrow();
        ProductRestorationSelfTest.expect(ErrorCode.PRODUCT_ALREADY_SOLD, () -> repository.restoreProduct(sold.getProductId(), 1L));
        TestSupport.equals(productRows, rows(factory, "products", "product_id"), "idempotent and rejected restores change no product column, including image and timestamps");
        TestSupport.equals(before, history(factory), "repeated/rejected restore preserves every bid, result and ban");
    }

    private static Map<String, List<List<String>>> history(JdbcConnectionFactory factory) throws Exception {
        Map<String, List<List<String>>> state = new LinkedHashMap<>();
        state.put("auctions", rows(factory, "auctions", "auction_id"));
        state.put("bids", rows(factory, "bids", "bid_id"));
        state.put("auction_results", rows(factory, "auction_results", "auction_id"));
        state.put("auction_blocked_users", rows(factory, "auction_blocked_users", "auction_id, user_id"));
        return state;
    }

    private static List<List<String>> rows(JdbcConnectionFactory factory, String table, String order) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        try (Connection connection = factory.openDatabase(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT * FROM " + table + " ORDER BY " + order)) {
            int columns = result.getMetaData().getColumnCount();
            while (result.next()) {
                List<String> row = new ArrayList<>();
                for (int column = 1; column <= columns; column++) {
                    Object value = result.getObject(column);
                    row.add(value instanceof byte[] bytes ? Base64.getEncoder().encodeToString(bytes) : result.getString(column));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private static void requireIsolated(JdbcConnectionFactory factory) {
        TestSupport.check(factory.getConfig().getDbName().startsWith("btl16_verify_"),
                "refusing to test against a non-isolated database");
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
