package vn.ptit.btl16.selftest;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.common.protocol.ProductRestorationStatus;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.AuctionStatus;
import vn.ptit.btl16.server.auction.model.Product;
import vn.ptit.btl16.server.auction.model.RoomVisibility;
import vn.ptit.btl16.server.auction.repository.AuctionRepository;
import vn.ptit.btl16.server.auction.repository.CloseAuctionCommit;
import vn.ptit.btl16.server.auction.repository.CreateAuctionCommit;
import vn.ptit.btl16.server.auction.repository.CreateProductCommit;
import vn.ptit.btl16.server.auction.repository.TestAuctionRepository;
import vn.ptit.btl16.server.auction.repository.UpdateProductCommit;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.auction.service.AuctionWireData;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

public final class ProductRestorationSelfTest {
    private ProductRestorationSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        TestAuctionRepository repository = new TestAuctionRepository();
        verify(repository, productId -> repository.addProduct(inactive(
                repository.findProductById(productId).orElseThrow())));
        concurrency(repository, repository);
        System.out.println("[PASS] ProductRestorationSelfTest: ownership/auth, idempotence, unsold/sold/open, freeze, history and races");
    }

    static void verify(AuctionRepository repository, LongConsumer forceInactive) throws Exception {
        AuctionRulesSelfTest.Fixture fixture = new AuctionRulesSelfTest.Fixture(repository);
        byte[] image;
        try (var stream = ProductRestorationSelfTest.class.getResourceAsStream("/ui/auction.png")) {
            image = stream.readAllBytes();
        }
        Product fresh = product(fixture, "RESTORE_BASIC", image);
        long productId = fresh.getProductId();
        expect(ErrorCode.AUTH_REQUIRED, () -> fixture.management.restoreProduct(null, productId));
        expect(ErrorCode.AUCTION_FORBIDDEN, () -> fixture.management.restoreProduct(fixture.buyer, productId));
        expect(ErrorCode.PRODUCT_NOT_FOUND, () -> fixture.management.restoreProduct(fixture.host, Long.MAX_VALUE));
        assertNoOp(fixture, repository, productId);
        fixture.sessions.detachByConnection("host");
        expect(ErrorCode.AUTH_REQUIRED, () -> fixture.management.restoreProduct(
                fixture.sessions.findByToken(fixture.host.getSessionToken()).orElseThrow(), productId));
        fixture.sessions.resumeSession(fixture.host.getSessionToken(), "host", "local");

        fixture.management.deactivateProduct(fixture.host, productId);
        expect(ErrorCode.AUCTION_FORBIDDEN, () -> fixture.management.restoreProduct(fixture.buyer, productId));
        expect(ErrorCode.AUTH_REQUIRED, () -> fixture.management.restoreProduct(null, productId));
        int productCount = repository.findProductsByOwner(1L).size();
        int auctionCount = repository.findAllAuctions().size();
        Product restored = fixture.management.restoreProduct(fixture.host, productId);
        TestSupport.check(restored.isActive(), "owner restores an eligible inactive product");
        TestSupport.equals(productId, restored.getProductId(), "restoration preserves identity");
        TestSupport.equals(fresh.getCode(), restored.getCode(), "restoration preserves code");
        TestSupport.equals(productCount, repository.findProductsByOwner(1L).size(), "restoration makes no duplicate product");
        TestSupport.equals(auctionCount, repository.findAllAuctions().size(), "restoration creates no auction");
        assertNoOp(fixture, repository, productId);

        for (boolean cancel : List.of(true, false)) {
            Product archived = product(fixture, cancel ? "RESTORE_CANCELLED" : "RESTORE_UNSOLD", image);
            long archivedId = archived.getProductId();
            AuctionSnapshot old = fixture.management.createAuction(fixture.host, archivedId,
                    new BigDecimal("100"), new BigDecimal("10"), 1, RoomVisibility.PRIVATE, "room1234");
            fixture.management.joinAuction(fixture.viewer, "viewer", old.getAuctionId(), "room1234");
            fixture.management.kickUser(fixture.host, old.getAuctionId(), "viewer");
            fixture.management.extendAuction(fixture.host, old.getAuctionId(), 60);
            if (cancel) {
                fixture.management.cancelAuction(fixture.host, old.getAuctionId());
            } else {
                fixture.management.endAuction(fixture.host, old.getAuctionId());
            }
            Map<String, String> history = archive(repository, old.getAuctionId());
            fixture.management.deactivateProduct(fixture.host, archivedId);
            TestSupport.equals(ProductRestorationStatus.ELIGIBLE,
                    fixture.management.restorationStatus(repository.findProductById(archivedId).orElseThrow()),
                    "cancelled/ended without a winner is restorable");
            int beforeCount = repository.findAllAuctions().size();
            fixture.management.restoreProduct(fixture.host, archivedId);
            TestSupport.equals(beforeCount, repository.findAllAuctions().size(), "restore does not reopen or create auctions");
            TestSupport.equals(history, archive(repository, old.getAuctionId()), "old times, caps, results, version and room settings survive restore");
            TestSupport.check(repository.isAuctionUserBlocked(old.getAuctionId(), 3L), "old participant ban survives restore");
            TestSupport.check(Arrays.equals(image, java.nio.file.Files.readAllBytes(fixture.management.media().config().root().resolve("products").resolve(repository.findProductMedia(archivedId).get(0).id()).resolve("source"))), "image bytes survive restore");
            TestSupport.equals(archived.getImageVersion(), repository.findProductById(archivedId).orElseThrow().getImageVersion(), "image version survives restore");
            assertFrozen(fixture, repository, archived, image);
            assertNoOp(fixture, repository, archivedId);
            AuctionSnapshot next = fixture.management.createAuction(fixture.host, archivedId,
                    new BigDecimal("100"), new BigDecimal("10"), 1);
            TestSupport.check(next.getAuctionId() != old.getAuctionId(), "unsold item can open a genuinely new auction");
            TestSupport.equals(history, archive(repository, old.getAuctionId()), "new auction leaves old history unchanged");
            TestSupport.check(!repository.isAuctionUserBlocked(next.getAuctionId(), 3L), "old bans remain scoped to their original auction");
        }

        Product sold = product(fixture, "RESTORE_SOLD", image);
        AuctionSnapshot soldAuction = fixture.management.createAuction(fixture.host, sold.getProductId(),
                new BigDecimal("100"), new BigDecimal("10"), 1);
        fixture.management.joinAuction(fixture.buyer, "buyer", soldAuction.getAuctionId());
        fixture.bids.placeBid(fixture.buyer, "buyer", soldAuction.getAuctionId(), new BigDecimal("110"));
        closeExpired(repository, soldAuction.getAuctionId());
        assertNoOp(fixture, repository, sold.getProductId());
        expect(ErrorCode.AUCTION_FORBIDDEN, () -> fixture.management.restoreProduct(fixture.buyer, sold.getProductId()));
        expect(ErrorCode.PRODUCT_ALREADY_SOLD, () -> fixture.management.createAuction(fixture.host,
                sold.getProductId(), new BigDecimal("100"), new BigDecimal("10"), 1));
        fixture.management.deactivateProduct(fixture.host, sold.getProductId());
        Map<String, String> soldState = archive(repository, soldAuction.getAuctionId());
        Map<String, String> inactiveSold = productState(repository.findProductById(sold.getProductId()).orElseThrow());
        expect(ErrorCode.PRODUCT_ALREADY_SOLD, () -> fixture.management.restoreProduct(fixture.host, sold.getProductId()));
        expect(ErrorCode.PRODUCT_ALREADY_SOLD, () -> repository.restoreProduct(sold.getProductId(), 1L));
        TestSupport.equals(inactiveSold, productState(repository.findProductById(sold.getProductId()).orElseThrow()), "rejected sold restore changes no product data");
        TestSupport.equals(soldState, archive(repository, soldAuction.getAuctionId()), "winner and accepted bid history survive rejected restore");

        Product open = product(fixture, "RESTORE_OPEN", image);
        AuctionSnapshot openAuction = fixture.management.createAuction(fixture.host, open.getProductId(),
                new BigDecimal("100"), new BigDecimal("10"), 1);
        forceInactive.accept(open.getProductId());
        Map<String, String> openState = archive(repository, openAuction.getAuctionId());
        expect(ErrorCode.PRODUCT_IN_USE, () -> fixture.management.restoreProduct(fixture.host, open.getProductId()));
        expect(ErrorCode.PRODUCT_IN_USE, () -> repository.restoreProduct(open.getProductId(), 1L));
        expect(ErrorCode.AUCTION_FORBIDDEN, () -> repository.restoreProduct(open.getProductId(), 2L));
        TestSupport.check(!repository.findProductById(open.getProductId()).orElseThrow().isActive(), "inactive item with an open auction stays inactive");
        TestSupport.equals(openState, archive(repository, openAuction.getAuctionId()), "rejected open restore changes no auction data");
        staleRestore(repository);
    }

    static void concurrency(AuctionRepository first, AuctionRepository second) throws Exception {
        Product duplicate = TestMedia.create(first, new CreateProductCommit(1L, "host", "RESTORE_REPEAT_RACE", "Repeated restore", "", Instant.now()));
        first.deactivateProduct(duplicate.getProductId(), 1L);
        List<Product> restored = race(List.of(
                () -> first.restoreProduct(duplicate.getProductId(), 1L),
                () -> second.restoreProduct(duplicate.getProductId(), 1L),
                () -> first.restoreProduct(duplicate.getProductId(), 1L),
                () -> second.restoreProduct(duplicate.getProductId(), 1L)));
        Map<String, String> once = productState(restored.get(0));
        for (Product value : restored) {
            TestSupport.equals(once, productState(value), "simultaneous restores perform only one transition, including timestamp");
        }

        for (int round = 0; round < 12; round++) {
            Product product = TestMedia.create(first, new CreateProductCommit(1L, "host", "RESTORE_RACE_" + round, "Race", "", Instant.now()));
            long productId = product.getProductId();
            first.deactivateProduct(productId, 1L);
            Instant start = Instant.now();
            CreateAuctionCommit commit = new CreateAuctionCommit(1L, "host", productId,
                    new BigDecimal("100"), new BigDecimal("10"), start, start.plusSeconds(60));
            List<ErrorCode> outcomes = race(List.of(
                    () -> outcome(() -> first.restoreProduct(productId, 1L)),
                    () -> outcome(() -> second.restoreProduct(productId, 1L)),
                    () -> outcome(() -> first.createAuction(commit)),
                    () -> outcome(() -> second.createAuction(commit)),
                    () -> outcome(() -> first.deactivateProduct(productId, 1L)),
                    () -> outcome(() -> second.deactivateProduct(productId, 1L))));
            for (ErrorCode code : outcomes) {
                TestSupport.check(code == null || Set.of(ErrorCode.PRODUCT_INACTIVE, ErrorCode.PRODUCT_IN_USE,
                        ErrorCode.AUCTION_ALREADY_EXISTS).contains(code), "concurrent request has only a valid domain outcome");
            }
            long openCount = first.findAllAuctions().stream().filter(auction ->
                    auction.getProduct().getProductId() == productId && auction.getStatus() == AuctionStatus.OPEN).count();
            TestSupport.check(openCount <= 1, "restore/create/deactivate race preserves one open auction");
            TestSupport.check(openCount == 0 || first.findProductById(productId).orElseThrow().isActive(),
                    "deactivation cannot race past a newly opened auction");
        }
    }

    private static void staleRestore(AuctionRepository repository) throws Exception {
        AuctionRulesSelfTest.Fixture normal = new AuctionRulesSelfTest.Fixture(repository);
        Product product = TestMedia.create(normal.management, normal.host, "RESTORE_STALE", "Stale check", "");
        long productId = product.getProductId();
        normal.management.deactivateProduct(normal.host, productId);
        CountDownLatch checked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AuctionRepository delayed = (AuctionRepository) Proxy.newProxyInstance(AuctionRepository.class.getClassLoader(),
                new Class<?>[]{AuctionRepository.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("restoreProduct")) {
                        checked.countDown();
                        TestSupport.check(release.await(10, TimeUnit.SECONDS), "stale restore released");
                    }
                    try {
                        return method.invoke(repository, arguments);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
        AuctionRulesSelfTest.Fixture stale = new AuctionRulesSelfTest.Fixture(delayed);
        CompletableFuture<ErrorCode> pending = CompletableFuture.supplyAsync(() ->
                outcome(() -> stale.management.restoreProduct(stale.host, productId)));
        try {
            TestSupport.check(checked.await(10, TimeUnit.SECONDS), "restoration passed its advisory precheck");
            normal.management.restoreProduct(normal.host, productId);
            AuctionSnapshot auction = normal.management.createAuction(normal.host, productId,
                    new BigDecimal("100"), new BigDecimal("10"), 1);
            normal.management.joinAuction(normal.buyer, "buyer", auction.getAuctionId());
            normal.bids.placeBid(normal.buyer, "buyer", auction.getAuctionId(), new BigDecimal("110"));
            closeExpired(repository, auction.getAuctionId());
            normal.management.deactivateProduct(normal.host, productId);
            release.countDown();
            TestSupport.equals(ErrorCode.PRODUCT_ALREADY_SOLD, pending.get(15, TimeUnit.SECONDS),
                    "transaction rechecks a product sold after the service precheck");
            TestSupport.check(!repository.findProductById(productId).orElseThrow().isActive(), "stale restoration cannot reactivate sold item");
        } finally {
            release.countDown();
        }
    }

    private static Product product(AuctionRulesSelfTest.Fixture fixture, String code, byte[] image) {
        return TestMedia.create(fixture.management, fixture.host, code, "Restore item", "Original description",
                image, "image/png", "original.png");
    }

    static void closeExpired(AuctionRepository repository, long auctionId) {
        AuctionSnapshot current = repository.findAuctionById(auctionId).orElseThrow();
        TestSupport.check(repository.closeAuction(new CloseAuctionCommit(auctionId, current.getCurrentWinnerId(),
                current.getCurrentWinnerUsername(), current.getCurrentPrice(), current.getEndTime(), current.getEndTime())).isPresent(), "test auction closes with its original result");
    }

    private static void assertFrozen(AuctionRulesSelfTest.Fixture fixture, AuctionRepository repository, Product product, byte[] image) {
        long productId = product.getProductId();
        expect(ErrorCode.PRODUCT_FROZEN, () -> fixture.management.updateProduct(fixture.host, productId,
                "CHANGED", "Changed", "Changed"));
        expect(ErrorCode.PRODUCT_FROZEN, () -> fixture.management.updateProduct(fixture.host, productId,
                "CHANGED", "Changed", "Changed", image, "image/png", "changed.png"));
        expect(ErrorCode.PRODUCT_FROZEN, () -> repository.updateProduct(new UpdateProductCommit(productId, 1L,
                "CHANGED", "Changed", "Changed", Instant.now(), image, "image/png", "changed.png", true)));
        TestSupport.equals(product.getDescription(), repository.findProductById(productId).orElseThrow().getDescription(), "content remains frozen after restoration");
    }

    private static void assertNoOp(AuctionRulesSelfTest.Fixture fixture, AuctionRepository repository, long productId) {
        Map<String, String> before = productState(repository.findProductById(productId).orElseThrow());
        TestSupport.equals(before, productState(fixture.management.restoreProduct(fixture.host, productId)), "active restore is a no-op including updatedAt");
        TestSupport.equals(before, productState(repository.restoreProduct(productId, 1L)), "repository active restore is a no-op");
        expect(ErrorCode.AUCTION_FORBIDDEN, () -> repository.restoreProduct(productId, 2L));
    }

    private static Map<String, String> archive(AuctionRepository repository, long auctionId) {
        AuctionSnapshot value = repository.findAuctionById(auctionId).orElseThrow();
        Map<String, String> state = new LinkedHashMap<>(AuctionWireData.snapshot(value,
                repository.findRecentBids(auctionId, 100), 0, Instant.EPOCH));
        state.put("passwordHash", value.getRoomPasswordHash());
        state.put("passwordSalt", value.getRoomPasswordSalt());
        state.put("passwordIterations", Integer.toString(value.getRoomPasswordIterations()));
        return state;
    }

    private static Map<String, String> productState(Product product) {
        return AuctionWireData.product(product, Instant.EPOCH);
    }

    private static Product inactive(Product current) {
        return new Product(current.getProductId(), current.getOwnerId(), current.getOwnerUsername(),
                current.getCode(), current.getName(), current.getDescription(), false, current.getCreatedAt(),
                current.getUpdatedAt(), current.hasImage(), current.getImageMime(), current.getImageName(),
                current.getImageSize(), current.getImageVersion());
    }

    static void expect(ErrorCode expected, Runnable action) {
        TestSupport.equals(expected, outcome(action), "expected domain rejection");
    }

    private static ErrorCode outcome(Runnable action) {
        try {
            action.run();
            return null;
        } catch (AuctionException exception) {
            return exception.getErrorCode();
        }
    }

    private static <Result> List<Result> race(List<Callable<Result>> actions) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(actions.size());
        CountDownLatch ready = new CountDownLatch(actions.size());
        CountDownLatch gate = new CountDownLatch(1);
        try {
            List<Future<Result>> futures = new ArrayList<>();
            for (Callable<Result> action : actions) {
                futures.add(workers.submit(() -> {
                    ready.countDown();
                    TestSupport.check(gate.await(10, TimeUnit.SECONDS), "concurrent request start gate");
                    return action.call();
                }));
            }
            TestSupport.check(ready.await(10, TimeUnit.SECONDS), "all concurrent requests are ready");
            gate.countDown();
            List<Result> results = new ArrayList<>();
            for (Future<Result> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            gate.countDown();
            workers.shutdownNow();
            workers.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
