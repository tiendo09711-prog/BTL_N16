package vn.ptit.btl16.selftest;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.server.account.repository.*;
import vn.ptit.btl16.server.account.security.PasswordHash;
import vn.ptit.btl16.server.account.service.AccountException;
import vn.ptit.btl16.server.db.DatabaseSchema;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Old databases, concurrent same-name accounts, leading zeros and stable profile edits. */
public final class AccountTagsSelfTest {
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        String oldConfig = System.getProperty("btl16.server.config"), oldData = System.getProperty("btl16.dataDir");
        Path directory = Files.createTempDirectory("btl16-account-tags-"), config = directory.resolve("server.properties");
        Files.writeString(config, "db.driver=org.h2.Driver\ndb.name=account_tags\ndb.user=sa\ndb.password=\n");
        System.setProperty("btl16.server.config", config.toString()); System.setProperty("btl16.dataDir", directory.toString());
        try {
            var factory = new JdbcConnectionFactory(ServerConfig.loadDefault()); DatabaseSchema.initialize(factory);
            var users = new JdbcUserRepository(factory); var hash = new PasswordHash("fixture-salt", "fixture-hash", 10000);
            long first = users.createUser("tag_first", "Nguyễn Minh Anh", "first@example.test", "", hash);
            long second = users.createUser("tag_second", "Nguyễn Minh Anh", "", "", hash);
            // Recreate the shape of a database from before account codes existed, preserving its users.
            try (var connection = factory.openDatabase(); var sql = connection.createStatement()) {
                sql.executeUpdate("DROP INDEX uq_users_friend_tag"); sql.executeUpdate("ALTER TABLE users DROP COLUMN friend_code");
            }
            AccountTags.initialize(factory);
            var migrated = users.findById(first).orElseThrow(); var other = users.findById(second).orElseThrow();
            TestSupport.check(migrated.getFriendCode().matches("[0-9]{4}") && !migrated.getFriendCode().equals(other.getFriendCode()), "migration gives old identically named accounts distinct four-digit tags");
            TestSupport.equals("first@example.test", migrated.getEmail(), "migration preserves existing account information");
            TestSupport.equals(hash.getHashBase64(), migrated.getPasswordHash(), "migration preserves passwords");
            AccountTags.initialize(factory);
            TestSupport.equals(migrated.getFriendCode(), users.findById(first).orElseThrow().getFriendCode(), "repeated startup does not regenerate account tags");
            var pool = Executors.newFixedThreadPool(6); var start = new CountDownLatch(1); var attempts = new ArrayList<Future<Long>>();
            try {
                for (int index = 0; index < 24; index++) {
                    int item = index; attempts.add(pool.submit(() -> { start.await(); return new JdbcUserRepository(factory).createUser("tag_parallel_" + item, "Cùng tên", "", "", hash); }));
                }
                start.countDown(); var codes = new HashSet<String>();
                for (var attempt : attempts) { TestSupport.check(codes.add(users.findById(attempt.get(30, TimeUnit.SECONDS)).orElseThrow().getFriendCode()), "concurrent same-name registrations never share a tag"); }
            } finally { pool.shutdownNow(); pool.awaitTermination(5, TimeUnit.SECONDS); }
            users.updateProfile(first, "Tên mới", migrated.getEmail(), "0900000000");
            TestSupport.equals(migrated.getFriendCode(), users.findById(first).orElseThrow().getFriendCode(), "profile edits preserve the assigned code");
            // Identical four-digit codes can be used by different names, keeping the namespace open.
            try (var connection = factory.openDatabase(); var update = connection.prepareStatement("UPDATE users SET friend_code='0007' WHERE user_id=? OR user_id=?")) {
                update.setLong(1, first); update.setLong(2, second); update.executeUpdate();
            }
            TestSupport.equals(first, users.findByDisplayNameAndFriendCode("Tên mới", "0007").orElseThrow().getUserId(), "leading zeros survive storage and lookup");
            try { users.updateProfile(second, "Tên mới", "", ""); throw new AssertionError("colliding name and code must be rejected"); }
            catch (AccountException expected) { TestSupport.equals("Nguyễn Minh Anh", users.findById(second).orElseThrow().getDisplayName(), "a rejected rename leaves the original account intact"); }
            System.out.println("[PASS] AccountTagsSelfTest: legacy migration, stable codes, concurrent duplicate names, leading zeros and rename collision protection");
        } finally {
            restore("btl16.server.config", oldConfig); restore("btl16.dataDir", oldData);
        }
    }
    private static void restore(String key, String value) { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } }
}
