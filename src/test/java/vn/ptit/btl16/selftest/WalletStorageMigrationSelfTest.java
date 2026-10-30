package vn.ptit.btl16.selftest;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.wallet.WalletWire;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.db.DatabaseSchema;
import vn.ptit.btl16.server.wallet.WalletRepository;
import vn.ptit.btl16.server.wallet.WalletSchema;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import java.util.UUID;

public final class WalletStorageMigrationSelfTest {
    private WalletStorageMigrationSelfTest() { }
    public static void main(String[] args) throws Exception { run(Arrays.asList(args).contains("--mysql")); }
    public static void run() throws Exception { run(false); }

    private static void run(boolean mysql) throws Exception {
        String oldConfig = System.getProperty("btl16.server.config"), oldData = System.getProperty("btl16.dataDir");
        Path folder = Files.createTempDirectory("btl16-wallet-migration-");
        String database = "btl16_wallet_migration_" + UUID.randomUUID().toString().replace("-", "");
        JdbcConnectionFactory factory = null;
        try {
            Properties properties = new Properties();
            if (mysql) {
                try (var reader = Files.newBufferedReader(Path.of(oldConfig == null ? "config/server.properties" : oldConfig))) { properties.load(reader); }
            } else { properties.setProperty("db.driver", "org.h2.Driver"); properties.setProperty("db.user", "sa"); }
            properties.setProperty("db.name", database);
            Path configFile = folder.resolve("server.properties");
            try (var writer = Files.newBufferedWriter(configFile)) { properties.store(writer, null); }
            System.setProperty("btl16.server.config", configFile.toString()); System.setProperty("btl16.dataDir", folder.toString());
            factory = new JdbcConnectionFactory(ServerConfig.loadDefault());
            DatabaseSchema.initialize(factory); DatabaseSchema.initialize(factory);
            assertClean(factory);
            String pending = UUID.randomUUID().toString();
            seedLegacy(factory, pending);
            try (var connection = factory.openDatabase(); var statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE users ADD CONSTRAINT migration_test_failure CHECK(wallet_balance<10000)");
            }
            expectFailure(factory);
            equal("7500", scalar(factory, "SELECT wallet_balance FROM users WHERE user_id=1"), "failed migration rolls back wallet credit");
            equal("3000", scalar(factory, "SELECT vault_balance FROM users WHERE user_id=1"), "failed migration preserves stored funds");
            equal("4", scalar(factory, "SELECT COUNT(*) FROM wallet_entries WHERE entry_type LIKE 'VAULT%'") , "failed migration restores deleted transfers");
            equal("7500", scalar(factory, "SELECT balance_after FROM wallet_entries WHERE entry_id=5"), "failed migration rolls back historical balances");
            execute(factory, "ALTER TABLE users DROP CONSTRAINT migration_test_failure");
            execute(factory, "UPDATE users SET wallet_balance=9999999999999999.99 WHERE user_id=1");
            expectFailure(factory);
            equal("3000", scalar(factory, "SELECT vault_balance FROM users WHERE user_id=1"), "overflow never discards stored funds");
            execute(factory, "UPDATE users SET wallet_balance=7500 WHERE user_id=1");
            DatabaseSchema.initialize(factory); DatabaseSchema.initialize(factory);
            assertClean(factory);
            WalletRepository wallets = new WalletRepository(factory);
            equal("10500", wallets.account(1).balance(), "all stored money returns to its owner");
            equal("2000", wallets.account(1).reserved(), "auction reservation stays unchanged");
            equal("8500", wallets.account(1).available(), "available balance includes returned money but excludes holds");
            equal("4000", wallets.account(2).balance(), "an empty retired balance never changes the wallet");
            equal("0", wallets.account(3).balance(), "an unused account remains empty");
            equal("2000", scalar(factory, "SELECT amount FROM wallet_holds WHERE auction_id=1"), "auction hold survives cleanup");
            equal("5", scalar(factory, "SELECT COUNT(*) FROM wallet_entries"), "only retired transfers are deleted");
            equal("10000", scalar(factory, "SELECT balance_after FROM wallet_entries WHERE entry_id=3"), "historical hold uses consolidated balance");
            equal("10500", scalar(factory, "SELECT balance_after FROM wallet_entries WHERE entry_id=5"), "historical topup uses consolidated balance");
            assertPagination(wallets);
            var data = WalletWire.encode(wallets.account(1), wallets.requests(1, false), wallets.entries(1), wallets.withdrawals(1, false));
            TestSupport.check(data.keySet().stream().noneMatch(key -> key.toLowerCase(java.util.Locale.ROOT).contains("vault")), "wire data exposes no retired fields");
            TestSupport.check(Arrays.stream(MessageType.values()).noneMatch(type -> type.name().contains("VAULT")), "retired protocol messages are removed");
            TestSupport.check(Arrays.stream(ErrorCode.values()).noneMatch(code -> code.name().contains("VAULT")), "retired errors are removed");
            TestSupport.equals(1, wallets.requests(1, false).size(), "existing topup requests survive");
            TestSupport.equals("PENDING", wallets.withdrawals(1, false).getFirst().status(), "pending simulated withdrawal survives");
            wallets.reviewWithdrawal(pending, true, ""); wallets.reviewWithdrawal(pending, true, "");
            equal("9500", wallets.account(1).balance(), "existing withdrawal is debited exactly once after migration");
            equal("2000", wallets.account(1).reserved(), "withdrawal leaves bid reservation intact");
            for (String ddl : new String[]{
                    "ALTER TABLE users ADD COLUMN vault_balance DECIMAL(18,2) NOT NULL DEFAULT 0",
                    "ALTER TABLE wallet_entries ADD COLUMN vault_after DECIMAL(18,2) NOT NULL DEFAULT 0"}) {
                execute(factory, ddl); WalletSchema.initialize(factory); assertClean(factory);
                equal("9500", wallets.account(1).balance(), "resuming partial DDL never credits money again");
            }
            String topup = UUID.randomUUID().toString(); wallets.submit(1, topup, new BigDecimal("1000")); wallets.review(topup, true);
            equal("10500", wallets.account(1).balance(), "topup still works after cleanup");
            WalletSchema.initialize(factory); assertClean(factory);
            System.out.println("[PASS] WalletStorageMigrationSelfTest: " + (mysql ? "MySQL/MariaDB" : "H2") + " fresh/legacy schema, exact-once funds, history normalization, holds, rollback, overflow, partial DDL, protocol cleanup and simulated withdrawal");
        } finally {
            try {
                if (mysql && factory != null && database.equals(factory.getConfig().getDbName())) { DatabaseSchema.dropDatabase(factory); }
            } finally {
                restore("btl16.server.config", oldConfig); restore("btl16.dataDir", oldData);
                try (var files = Files.walk(folder)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) { Files.deleteIfExists(file); } }
            }
        }
    }

    private static void seedLegacy(JdbcConnectionFactory factory, String pending) throws Exception {
        try (var connection = factory.openDatabase(); var statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE users ADD COLUMN vault_balance DECIMAL(18,2) NOT NULL DEFAULT 0");
            statement.executeUpdate("ALTER TABLE wallet_entries ADD COLUMN vault_after DECIMAL(18,2) NOT NULL DEFAULT 0");
            statement.executeUpdate("CREATE TABLE wallet_vaults(user_id BIGINT PRIMARY KEY, pin_hash VARCHAR(128), pin_salt VARCHAR(64), pin_iterations INT, failed_attempts INT, locked_until TIMESTAMP NULL, FOREIGN KEY(user_id) REFERENCES users(user_id))");
            statement.executeUpdate("""
                    INSERT INTO users(user_id,username,display_name,password_hash,password_salt,password_iterations,wallet_balance,wallet_reserved,vault_balance) VALUES
                    (1,'legacy_owner','Owner','unused','unused',10000,7500,2000,3000),
                    (2,'legacy_empty','Empty','unused','unused',10000,4000,0,0),
                    (3,'legacy_new','New','unused','unused',10000,0,0,0)
                    """);
            statement.executeUpdate("INSERT INTO wallet_vaults(user_id,pin_hash,pin_salt,pin_iterations,failed_attempts) VALUES(1,'retired-hash','retired-salt',120000,3),(2,'empty-hash','empty-salt',120000,0)");
            statement.executeUpdate("INSERT INTO products(product_id,created_by,code,name) VALUES(1,2,'MIGRATION','Migration product')");
            statement.executeUpdate("INSERT INTO auctions(auction_id,host_user_id,product_id,start_price,current_price,start_time,end_time,hard_end_time,status) VALUES(1,2,1,1000,2000,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'OPEN')");
            statement.executeUpdate("INSERT INTO wallet_holds(auction_id,user_id,amount) VALUES(1,1,2000)");
            statement.executeUpdate("""
                    INSERT INTO wallet_entries(entry_id,user_id,entry_type,amount,reserved_delta,balance_after,reserved_after,reference_key,created_at,vault_after) VALUES
                    (1,1,'TOPUP',10000,0,10000,0,'TOPUP:legacy',CURRENT_TIMESTAMP,0),
                    (2,1,'VAULT_DEPOSIT',-4000,0,6000,0,'VAULT:deposit',CURRENT_TIMESTAMP,4000),
                    (3,1,'RESERVE',0,2000,6000,2000,'BID:legacy',CURRENT_TIMESTAMP,4000),
                    (4,1,'VAULT_WITHDRAWAL',1000,0,7000,2000,'VAULT:withdraw',CURRENT_TIMESTAMP,3000),
                    (5,1,'TOPUP',500,0,7500,2000,'TOPUP:extra',CURRENT_TIMESTAMP,3000),
                    (6,2,'TOPUP',4000,0,4000,0,'TOPUP:empty',CURRENT_TIMESTAMP,0),
                    (7,2,'VAULT_DEPOSIT',-2000,0,2000,0,'VAULT:empty-in',CURRENT_TIMESTAMP,2000),
                    (8,2,'VAULT_WITHDRAWAL',2000,0,4000,0,'VAULT:empty-out',CURRENT_TIMESTAMP,0),
                    (9,1,'LEGACY_REVIEW',0,0,7500,2000,'LEGACY:old',CURRENT_TIMESTAMP,3000)
                    """);
            statement.executeUpdate("INSERT INTO wallet_topups(request_id,user_id,amount,status,created_at) VALUES('" + UUID.randomUUID() + "',1,10000,'APPROVED',CURRENT_TIMESTAMP)");
            try (var insert = connection.prepareStatement("INSERT INTO wallet_withdrawals(request_id,user_id,amount,status,created_at) VALUES(?,1,1000,'PENDING',CURRENT_TIMESTAMP)")) {
                insert.setString(1, pending); insert.executeUpdate();
            }
        }
    }

    private static void assertClean(JdbcConnectionFactory factory) throws Exception {
        try (Connection connection = factory.openDatabase()) {
            try (var tables = connection.getMetaData().getTables(connection.getCatalog(), null, null, null)) {
                while (tables.next()) { TestSupport.check(!tables.getString("TABLE_NAME").toLowerCase(java.util.Locale.ROOT).contains("vault"), "no retired table survives or is recreated"); }
            }
            try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, null, null)) {
                while (columns.next()) { TestSupport.check(!columns.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT).contains("vault"), "no retired column survives or is recreated"); }
            }
            try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM wallet_entries WHERE entry_type LIKE 'VAULT%' OR reference_key LIKE 'VAULT:%'")) {
                rows.next(); TestSupport.equals(0L, rows.getLong(1), "no retired ledger data survives");
            }
        }
    }

    private static void expectFailure(JdbcConnectionFactory factory) {
        try { WalletSchema.initialize(factory); throw new AssertionError("invalid legacy migration must fail without losing data"); }
        catch (IllegalStateException expected) { TestSupport.check(expected.getCause() instanceof java.sql.SQLException, "migration reports its SQL failure"); }
    }
    private static void assertPagination(WalletRepository wallets) {
        for (boolean hideReservations : java.util.List.of(false, true)) {
            for (boolean hideLegacy : java.util.List.of(false, true)) {
                var seen = new java.util.HashSet<Long>(); long cursor = Long.MAX_VALUE; boolean more;
                do {
                    var page = wallets.entryPage(1, null, "", null, null, cursor, 1, hideReservations, hideLegacy);
                    var entries = WalletWire.entries(page);
                    TestSupport.check(!entries.isEmpty(), "filtering before pagination never leaves a blank page");
                    for (var entry : entries) {
                        TestSupport.check(seen.add(entry.id()), "pagination never repeats an entry");
                        TestSupport.check(!hideReservations || !java.util.Set.of("RESERVE", "RELEASE").contains(entry.type()), "personal history hides reservation bookkeeping");
                        TestSupport.check(!hideLegacy || !entry.type().equals("LEGACY_REVIEW"), "personal history hides legacy review bookkeeping");
                    }
                    more = Boolean.parseBoolean(page.get("hasMore")); cursor = Long.parseLong(page.get("nextBeforeId"));
                } while (more);
                long expected = wallets.entries(1).stream().filter(entry -> !hideReservations || !java.util.Set.of("RESERVE", "RELEASE").contains(entry.type()))
                        .filter(entry -> !hideLegacy || !entry.type().equals("LEGACY_REVIEW")).count();
                TestSupport.equals(expected, (long) seen.size(), "all surviving entries remain reachable through filtered pages");
            }
        }
        TestSupport.check(WalletWire.entries(wallets.entryPage(3, null, "", null, null, Long.MAX_VALUE, 1)).isEmpty(), "history never exposes another owner's entries");
        try {
            wallets.entryPage(1, null, "VAULT_DEPOSIT", null, null, Long.MAX_VALUE, 1);
            throw new AssertionError("retired transaction filters must be rejected");
        } catch (vn.ptit.btl16.server.auction.service.AuctionException expected) {
            TestSupport.equals(ErrorCode.VALIDATION_ERROR, expected.getErrorCode(), "retired transaction types are no longer supported");
        }
    }
    private static BigDecimal scalar(JdbcConnectionFactory factory, String sql) throws Exception {
        try (var connection = factory.openDatabase(); var query = connection.createStatement(); var rows = query.executeQuery(sql)) { rows.next(); return rows.getBigDecimal(1); }
    }
    private static void execute(JdbcConnectionFactory factory, String sql) throws Exception {
        try (var connection = factory.openDatabase(); var statement = connection.createStatement()) { statement.executeUpdate(sql); }
    }
    private static void equal(String expected, BigDecimal actual, String message) { TestSupport.check(new BigDecimal(expected).compareTo(actual) == 0, message + ": " + actual); }
    private static void restore(String key, String value) { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } }
}
