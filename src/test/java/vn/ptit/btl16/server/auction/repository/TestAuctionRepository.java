package vn.ptit.btl16.server.auction.repository;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.server.auction.model.AuctionResult;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.AuctionStatus;
import vn.ptit.btl16.server.auction.model.BidRecord;
import vn.ptit.btl16.server.auction.model.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class TestAuctionRepository implements AuctionRepository {
    private final ConcurrentHashMap<Long, Product> products = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AuctionSnapshot> auctions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<BidRecord>> bidsByAuction = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AuctionResult> results = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<Long>> blockedUsersByAuction = new ConcurrentHashMap<>();
    private final AtomicLong productIds = new AtomicLong();
    private final AtomicLong auctionIds = new AtomicLong();
    private final AtomicLong bidIds = new AtomicLong();

    public static TestAuctionRepository withDemoAuctions(ServerConfig config) {
        TestAuctionRepository repository = new TestAuctionRepository();
        Instant now = Instant.now();
        Product headset = repository.addProduct(new Product(
                1L, 3L, "bob", "HEADSET", "Tai nghe gaming",
                "Tai nghe khong day cho demo dau gia.", true, now, now));
        Product phone = repository.addProduct(new Product(
                2L, 3L, "bob", "PHONE", "Dien thoai thong minh",
                "Dien thoai demo, pin tot va man hinh dep.", true, now, now));
        Product laptop = repository.addProduct(new Product(
                3L, 3L, "bob", "LAPTOP", "Laptop gaming",
                "Laptop demo cho nhieu client cung dau gia.", true, now, now));
        repository.addAuction(demoAuction(
                1L, headset, now, config.getDemoShortAuctionSeconds(), "1000000.00"));
        repository.addAuction(demoAuction(
                2L, phone, now, config.getDemoMediumAuctionSeconds(), "5000000.00"));
        repository.addAuction(demoAuction(
                3L, laptop, now, config.getDemoLongAuctionSeconds(), "10000000.00"));
        return repository;
    }

    private static AuctionSnapshot demoAuction(
            long auctionId,
            Product product,
            Instant now,
            int durationSeconds,
            String price) {
        BigDecimal value = new BigDecimal(price);
        return new AuctionSnapshot(
                auctionId,
                product,
                3L,
                "bob",
                value,
                new BigDecimal("50000.00"),
                value,
                null,
                "",
                now.minusSeconds(5),
                now.plusSeconds(durationSeconds),
                AuctionStatus.OPEN,
                null,
                0L);
    }

    public Product addProduct(Product product) {
        products.put(product.getProductId(), product);
        productIds.accumulateAndGet(product.getProductId(), Math::max);
        return product;
    }

    public void addAuction(AuctionSnapshot snapshot) {
        addProduct(snapshot.getProduct());
        auctions.put(snapshot.getAuctionId(), snapshot);
        auctionIds.accumulateAndGet(snapshot.getAuctionId(), Math::max);
        bidsByAuction.putIfAbsent(snapshot.getAuctionId(), new ArrayList<>());
    }

    @Override
    public synchronized Product createProduct(CreateProductCommit commit) {
        if (findProductByCode(commit.getCode()).isPresent()) {
            throw new AuctionRepositoryException("Product code already exists");
        }
        long productId = productIds.incrementAndGet();
        Product product = new Product(
                productId, commit.getOwnerId(), commit.getOwnerUsername(),
                commit.getCode(), commit.getName(), commit.getDescription(),
                true, commit.getCreatedAt(), commit.getCreatedAt());
        products.put(productId, product);
        return product;
    }

    @Override
    public synchronized Product updateProduct(UpdateProductCommit commit) {
        Product current = products.get(commit.getProductId());
        if (current == null || current.getOwnerId() != commit.getOwnerId()) {
            throw new AuctionRepositoryException("Product was not found for owner");
        }
        Optional<Product> duplicate = findProductByCode(commit.getCode());
        if (duplicate.isPresent() && duplicate.get().getProductId() != commit.getProductId()) {
            throw new AuctionRepositoryException("Product code already exists");
        }
        Product updated = new Product(
                current.getProductId(), current.getOwnerId(), current.getOwnerUsername(),
                commit.getCode(), commit.getName(), commit.getDescription(),
                current.isActive(), current.getCreatedAt(), commit.getUpdatedAt());
        products.put(updated.getProductId(), updated);
        return updated;
    }

    @Override
    public synchronized Optional<Product> deactivateProduct(long productId, long ownerId) {
        Product current = products.get(productId);
        if (current == null || current.getOwnerId() != ownerId) {
            return Optional.empty();
        }
        Product updated = new Product(
                current.getProductId(), current.getOwnerId(), current.getOwnerUsername(),
                current.getCode(), current.getName(), current.getDescription(),
                false, current.getCreatedAt(), Instant.now());
        products.put(productId, updated);
        return Optional.of(updated);
    }

    @Override
    public List<Product> findProductsByOwner(long ownerId) {
        return products.values().stream()
                .filter(value -> value.getOwnerId() == ownerId)
                .sorted(Comparator.comparing(Product::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Override
    public Optional<Product> findProductById(long productId) {
        return Optional.ofNullable(products.get(productId));
    }

    @Override
    public Optional<Product> findProductByCode(String code) {
        return products.values().stream()
                .filter(value -> value.getCode().equalsIgnoreCase(code))
                .findFirst();
    }

    @Override
    public boolean hasOpenAuctionForProduct(long productId) {
        return auctions.values().stream().anyMatch(value ->
                value.getProduct().getProductId() == productId
                        && value.getStatus() == AuctionStatus.OPEN);
    }

    @Override
    public synchronized AuctionSnapshot createAuction(CreateAuctionCommit commit) {
        Product product = products.get(commit.getProductId());
        if (product == null) {
            throw new AuctionRepositoryException("Product does not exist");
        }
        long auctionId = auctionIds.incrementAndGet();
        AuctionSnapshot snapshot = new AuctionSnapshot(
                auctionId, product, commit.getHostUserId(), commit.getHostUsername(),
                commit.getStartPrice(), commit.getMinBidIncrement(), commit.getStartPrice(),
                null, "", commit.getStartTime(), commit.getEndTime(),
                AuctionStatus.OPEN, null, 0L);
        addAuction(snapshot);
        return snapshot;
    }

    @Override
    public List<AuctionSnapshot> findAllAuctions() {
        List<AuctionSnapshot> values = new ArrayList<>(auctions.values());
        values.sort(auctionComparator());
        return values;
    }

    @Override
    public List<AuctionSnapshot> findAuctionsByHost(long hostUserId) {
        return auctions.values().stream()
                .filter(value -> value.getHostUserId() == hostUserId)
                .sorted(auctionComparator())
                .toList();
    }

    private Comparator<AuctionSnapshot> auctionComparator() {
        return Comparator
                .comparing((AuctionSnapshot value) -> value.getStatus() == AuctionStatus.OPEN ? 0 : 1)
                .thenComparing(AuctionSnapshot::getEndTime);
    }

    @Override
    public Optional<AuctionSnapshot> findAuctionById(long auctionId) {
        return Optional.ofNullable(auctions.get(auctionId));
    }

    @Override
    public List<BidRecord> findRecentBids(long auctionId, int limit) {
        List<BidRecord> values = bidsByAuction.getOrDefault(auctionId, List.of());
        synchronized (values) {
            return values.stream()
                    .sorted(Comparator.comparingLong(BidRecord::getServerSequence).reversed())
                    .limit(Math.max(1, limit))
                    .toList();
        }
    }

    @Override
    public synchronized BidRecord commitAcceptedBid(BidCommit commit) {
        AuctionSnapshot current = auctions.get(commit.getAuctionId());
        if (current == null || current.getStatus() != AuctionStatus.OPEN) {
            throw new AuctionConflictException("Auction is not open");
        }
        if (current.getCurrentPrice().compareTo(commit.getExpectedCurrentPrice()) != 0) {
            throw new AuctionConflictException("Auction price changed concurrently");
        }
        if (!commit.getCreatedAt().isBefore(current.getEndTime())) {
            throw new AuctionConflictException("Bid arrived after end time");
        }
        long bidId = bidIds.incrementAndGet();
        BidRecord bid = new BidRecord(
                bidId, commit.getAuctionId(), commit.getBidderId(), commit.getBidderUsername(),
                commit.getAmount(), commit.getServerSequence(), commit.getCreatedAt());
        List<BidRecord> list = bidsByAuction.computeIfAbsent(commit.getAuctionId(), key -> new ArrayList<>());
        synchronized (list) {
            list.add(bid);
        }
        auctions.put(current.getAuctionId(), copyAuction(
                current, commit.getAmount(), commit.getBidderId(), commit.getBidderUsername(),
                commit.getNewEndTime(), current.getStatus(), current.getEndedAt()));
        return bid;
    }

    @Override
    public synchronized boolean extendAuction(ExtendAuctionCommit commit) {
        AuctionSnapshot current = auctions.get(commit.getAuctionId());
        if (current == null || current.getStatus() != AuctionStatus.OPEN
                || !current.getEndTime().equals(commit.getExpectedEndTime())) {
            return false;
        }
        auctions.put(current.getAuctionId(), copyAuction(
                current, current.getCurrentPrice(), current.getCurrentWinnerId(),
                current.getCurrentWinnerUsername(), commit.getNewEndTime(),
                current.getStatus(), current.getEndedAt()));
        return true;
    }

    @Override
    public synchronized Optional<AuctionResult> closeAuction(CloseAuctionCommit commit) {
        AuctionSnapshot current = auctions.get(commit.getAuctionId());
        if (current == null || current.getStatus() != AuctionStatus.OPEN) {
            return Optional.empty();
        }
        if (commit.isRequireExpired() && commit.getEndedAt().isBefore(current.getEndTime())) {
            return Optional.empty();
        }
        auctions.put(current.getAuctionId(), copyAuction(
                current, current.getCurrentPrice(), current.getCurrentWinnerId(),
                current.getCurrentWinnerUsername(), current.getEndTime(),
                AuctionStatus.ENDED, commit.getEndedAt()));
        AuctionResult result = new AuctionResult(
                current.getAuctionId(), current.getCurrentWinnerId(),
                current.getCurrentWinnerUsername(), current.getCurrentPrice(), commit.getEndedAt());
        results.put(current.getAuctionId(), result);
        return Optional.of(result);
    }

    @Override
    public synchronized boolean cancelAuction(CancelAuctionCommit commit) {
        AuctionSnapshot current = auctions.get(commit.getAuctionId());
        if (current == null || current.getStatus() != AuctionStatus.OPEN || hasBids(commit.getAuctionId())) {
            return false;
        }
        auctions.put(current.getAuctionId(), copyAuction(
                current, current.getCurrentPrice(), current.getCurrentWinnerId(),
                current.getCurrentWinnerUsername(), current.getEndTime(),
                AuctionStatus.CANCELLED, commit.getCancelledAt()));
        return true;
    }

    private AuctionSnapshot copyAuction(
            AuctionSnapshot current,
            BigDecimal currentPrice,
            Long winnerId,
            String winnerUsername,
            Instant endTime,
            AuctionStatus status,
            Instant endedAt) {
        return new AuctionSnapshot(
                current.getAuctionId(), current.getProduct(),
                current.getHostUserId(), current.getHostUsername(),
                current.getStartPrice(), current.getMinBidIncrement(), currentPrice,
                winnerId, winnerUsername, current.getStartTime(), endTime,
                status, endedAt, current.getVersion() + 1);
    }

    @Override
    public boolean hasBids(long auctionId) {
        List<BidRecord> values = bidsByAuction.get(auctionId);
        return values != null && !values.isEmpty();
    }

    @Override
    public void blockAuctionUser(BlockAuctionUserCommit commit) {
        blockedUsersByAuction
                .computeIfAbsent(commit.getAuctionId(), key -> ConcurrentHashMap.newKeySet())
                .add(commit.getUserId());
    }

    @Override
    public boolean isAuctionUserBlocked(long auctionId, long userId) {
        Set<Long> values = blockedUsersByAuction.get(auctionId);
        return values != null && values.contains(userId);
    }

    @Override
    public long findMaxServerSequence() {
        long max = 0L;
        for (List<BidRecord> values : bidsByAuction.values()) {
            synchronized (values) {
                for (BidRecord value : values) {
                    max = Math.max(max, value.getServerSequence());
                }
            }
        }
        return max;
    }
}
