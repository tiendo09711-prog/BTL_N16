package vn.ptit.btl16.selftest;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.server.account.repository.JdbcConnectionFactory;
import vn.ptit.btl16.server.auction.model.Product;
import vn.ptit.btl16.server.auction.repository.*;
import vn.ptit.btl16.server.db.DatabaseSchema;
import vn.ptit.btl16.server.media.MediaProductCommit;
import vn.ptit.btl16.server.media.ProductMedia;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Persistent, global system codes tested against an isolated transactional database. */
public final class ProductCodesSelfTest {
    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        String oldConfig = System.getProperty("btl16.server.config"), oldData = System.getProperty("btl16.dataDir");
        Path directory = Files.createTempDirectory("btl16-product-codes-").toRealPath();
        Path settings = directory.resolve("server.properties");
        Files.writeString(settings, "db.driver=org.h2.Driver\ndb.name=product_codes\ndb.user=sa\ndb.password=\n");
        System.setProperty("btl16.server.config", settings.toString());
        System.setProperty("btl16.dataDir", directory.toString());
        try {
            var factory = new JdbcConnectionFactory(ServerConfig.loadDefault());
            DatabaseSchema.initialize(factory);
            try (var connection = factory.openDatabase(); var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO users(user_id, username, display_name, password_hash, password_salt, password_iterations)
                        VALUES(1, 'host', 'Host', 'test', 'test', 30000), (2, 'buyer', 'Buyer', 'test', 'test', 30000)
                        """);
            }
            var first = new JdbcAuctionRepository(factory);
            Product legacy = first.createProduct(commit(1, ""));
            TestSupport.equals("MH1", legacy.getCode(), "first code requires no user input");
            // Simulate an existing manually coded product before the feature was installed.
            try (var connection = factory.openDatabase(); var query = connection.prepareStatement("UPDATE products SET code='MH2' WHERE product_id=?")) {
                query.setLong(1, legacy.getProductId()); query.executeUpdate();
            }
            var second = new JdbcAuctionRepository(factory);
            Product otherSeller = second.createProduct(commit(2, "MH2"));
            TestSupport.equals("MH3", otherSeller.getCode(), "global sequence skips an existing legacy MH code");
            Product edited = first.updateProduct(new UpdateProductCommit(legacy.getProductId(), 1, "MH3", "Edited", TestMedia.DESCRIPTION, Instant.now()));
            TestSupport.equals("MH2", edited.getCode(), "legacy repository update cannot change a code");

            long count = scalar(factory, "SELECT COUNT(*) FROM products");
            long next = scalar(factory, "SELECT next_number FROM product_code_sequence");
            var failed = mediaCommit(1, "ignored", false);
            try {
                first.saveMediaProduct(failed);
                throw new AssertionError("missing media payload must fail");
            } catch (AuctionRepositoryException expected) {
                TestSupport.equals(count, scalar(factory, "SELECT COUNT(*) FROM products"), "failed media save creates no product");
                TestSupport.equals(next, scalar(factory, "SELECT next_number FROM product_code_sequence"), "failed transaction rolls back allocation");
                TestSupport.check(first.findMedia(failed.media().get(0).id()).isEmpty(), "failed transaction creates no media metadata");
            }
            var media = mediaCommit(1, "! invalid requested code !", true);
            Product saved = first.saveMediaProduct(media);
            TestSupport.equals("MH4", saved.getCode(), "media creation ignores supplied code and follows the global sequence");
            Product updated = first.saveMediaProduct(new MediaProductCommit(saved.getProductId(), 1, "host", "MH2", "Media edited",
                    TestMedia.DESCRIPTION, first.findProductMedia(saved.getProductId())));
            TestSupport.equals(saved.getCode(), updated.getCode(), "media update preserves code even if a client sends another code");

            // Separate repository instances/connections exercise the database lock for both creation paths.
            int attempts = 20;
            var pool = Executors.newFixedThreadPool(6);
            var start = new CountDownLatch(1);
            List<Future<Product>> futures = new ArrayList<>();
            try {
                for (int index = 0; index < attempts; index++) {
                    int item = index;
                    futures.add(pool.submit(() -> {
                        start.await();
                        var repository = item % 2 == 0 ? first : second;
                        long owner = item % 2 + 1;
                        return item % 3 == 0 ? repository.saveMediaProduct(mediaCommit(owner, "MH2", true))
                                : repository.createProduct(commit(owner, item % 2 == 0 ? "" : "MH2"));
                    }));
                }
                start.countDown();
                Set<String> codes = new HashSet<>();
                for (var future : futures) {
                    Product product = future.get(45, TimeUnit.SECONDS);
                    TestSupport.check(product.getCode().matches("MH[1-9][0-9]*") && codes.add(product.getCode()), "concurrent sellers receive unique system codes");
                }
                for (int number = 5; number < 5 + attempts; number++) {
                    TestSupport.check(codes.contains("MH" + number), "sequence remains consecutive across concurrent commits");
                }
            } finally { start.countDown(); pool.shutdownNow(); pool.awaitTermination(10, TimeUnit.SECONDS); }

            Product last = first.createProduct(commit(1, null));
            first.deleteProduct(last.getProductId(), 1);
            DatabaseSchema.initialize(factory);
            Product restarted = new JdbcAuctionRepository(new JdbcConnectionFactory(ServerConfig.loadDefault())).createProduct(commit(2, last.getCode()));
            TestSupport.equals("MH" + (Long.parseLong(last.getCode().substring(2)) + 1), restarted.getCode(), "reinitializing and deleting do not reset or reuse issued codes");
            TestSupport.equals("MH2", first.findProductById(legacy.getProductId()).orElseThrow().getCode(), "old codes remain unchanged");
            TestSupport.equals(scalar(factory, "SELECT COUNT(*) FROM products"), scalar(factory, "SELECT COUNT(DISTINCT code) FROM products"), "database contains no duplicate codes");
            System.out.println("[PASS] ProductCodesSelfTest: automatic global codes, concurrent media/legacy saves, collision skipping, immutable edits, rollback, deletion and restart");
        } finally {
            restore("btl16.server.config", oldConfig); restore("btl16.dataDir", oldData);
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    TestSupport.check(path.toAbsolutePath().normalize().startsWith(directory), "cleanup stays in the isolated test directory");
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static CreateProductCommit commit(long owner, String requestedCode) {
        return new CreateProductCommit(owner, owner == 1 ? "host" : "buyer", requestedCode, "Product", TestMedia.DESCRIPTION, Instant.now());
    }

    private static MediaProductCommit mediaCommit(long owner, String requestedCode, boolean payload) {
        var media = new ProductMedia(UUID.randomUUID().toString(), 0, "IMAGE", "fixture.png", "image/png", 3, 1, 1, 0, 0);
        Map<String, Map<String, byte[]>> content = payload ? Map.of(media.id(), Map.of("source", new byte[]{1, 2, 3},
                "detail.jpg", new byte[]{1, 2, 3}, "thumbnail.jpg", new byte[]{1, 2, 3})) : Map.of();
        return new MediaProductCommit(0, owner, owner == 1 ? "host" : "buyer", requestedCode, "Media product", TestMedia.DESCRIPTION, List.of(media), content);
    }

    private static long scalar(JdbcConnectionFactory factory, String sql) throws Exception {
        try (var connection = factory.openDatabase(); var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next(); return rows.getLong(1);
        }
    }
    private static void restore(String key, String value) { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } }
}
