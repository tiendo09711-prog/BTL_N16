package vn.ptit.btl16.server.auction.repository;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.selftest.TestSupport;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;

import java.nio.file.Files;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class RoomCodeStoreSelfTest {
    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        String oldConfig = System.getProperty("btl16.server.config");
        String oldData = System.getProperty("btl16.dataDir");
        var directory = Files.createTempDirectory("btl16-room-codes-store-");
        var settings = directory.resolve("server.properties");
        Files.writeString(settings, "db.driver=org.h2.Driver\ndb.name=room_codes\ndb.user=sa\ndb.password=\n");
        System.setProperty("btl16.server.config", settings.toString());
        System.setProperty("btl16.dataDir", directory.toString());
        try {
            var factory = new JdbcConnectionFactory(ServerConfig.loadDefault());
            try (var connection = factory.openDatabase(); var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE auctions (auction_id BIGINT PRIMARY KEY)");
                statement.executeUpdate("INSERT INTO auctions VALUES (1), (2), (3)");
            }
            RoomCodeStore.initialize(factory);
            Map<Long, String> original = codes(factory);
            TestSupport.equals(3, new HashSet<>(original.values()).size(), "legacy rooms receive unique codes");
            TestSupport.check(original.values().stream().allMatch(code -> code.matches("[0-9]{6}")), "backfilled codes contain six ASCII digits");
            RoomCodeStore.initialize(factory);
            TestSupport.equals(original, codes(factory), "schema migration preserves existing codes on restart");

            try (var connection = factory.openDatabase(); var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE auctions SET room_code=NULL");
                connection.setAutoCommit(false);
                TestSupport.equals("000007", RoomCodeStore.allocate(connection, 1, () -> 7), "leading zeroes are retained");
                AtomicInteger attempts = new AtomicInteger();
                TestSupport.equals("000008", RoomCodeStore.allocate(connection, 2,
                        () -> attempts.getAndIncrement() == 0 ? 7 : 8), "collision retries with another code");
                TestSupport.equals(2, attempts.get(), "duplicate key is retried exactly once");
                connection.commit();
                RoomCodeStore.allocate(connection, 3, () -> 9);
                connection.rollback();
                TestSupport.equals(null, codes(factory).get(3L), "allocation rolls back with the auction transaction");
                try {
                    RoomCodeStore.allocate(connection, 3, () -> 7);
                    throw new AssertionError("repeated collisions must fail instead of reusing a code");
                } catch (SQLException expected) {
                    TestSupport.check(expected.getMessage().contains("unused"), "exhausted retries report allocation failure");
                }
                connection.rollback();
            }
            RoomCodeStore.initialize(factory);
            TestSupport.equals("000007", codes(factory).get(1L), "backfill does not replace assigned codes");
            TestSupport.check(codes(factory).get(3L).matches("[0-9]{6}"), "missing code is backfilled");

            try (var connection = factory.openDatabase(); var insert = connection.prepareStatement("INSERT INTO auctions(auction_id) VALUES(?)")) {
                for (long auctionId = 4; auctionId < 28; auctionId++) { insert.setLong(1, auctionId); insert.addBatch(); }
                insert.executeBatch();
            }
            try (var workers = Executors.newFixedThreadPool(6)) {
                var futures = new java.util.ArrayList<java.util.concurrent.Future<String>>();
                for (long auctionId = 4; auctionId < 28; auctionId++) {
                    long room = auctionId;
                    futures.add(workers.submit(() -> {
                        try (var connection = factory.openDatabase()) {
                            connection.setAutoCommit(false);
                            String code = RoomCodeStore.allocate(connection, room);
                            connection.commit();
                            return code;
                        }
                    }));
                }
                for (var future : futures) { TestSupport.check(future.get(30, TimeUnit.SECONDS).matches("[0-9]{6}"), "concurrent code format"); }
            }
            Map<Long, String> concurrent = codes(factory);
            TestSupport.equals(27, new HashSet<>(concurrent.values()).size(), "concurrently created rooms never share codes");
        } finally {
            restore("btl16.server.config", oldConfig);
            restore("btl16.dataDir", oldData);
        }
        System.out.println("[PASS] RoomCodeStoreSelfTest: legacy migration, stable codes, leading zeroes, collision retry, rollback and concurrent allocation");
    }

    private static Map<Long, String> codes(JdbcConnectionFactory factory) throws SQLException {
        var result = new LinkedHashMap<Long, String>();
        try (var connection = factory.openDatabase(); var query = connection.createStatement();
             var rows = query.executeQuery("SELECT auction_id, room_code FROM auctions ORDER BY auction_id")) {
            while (rows.next()) { result.put(rows.getLong(1), rows.getString(2)); }
        }
        return result;
    }

    private static void restore(String key, String value) {
        if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); }
    }
}
