package vn.ptit.btl16.server.auction.repository;
import vn.ptit.btl16.server.media.ProductMedia;
import vn.ptit.btl16.server.media.MediaProductCommit;
import vn.ptit.btl16.server.media.ProductRequirements;
import java.util.Map;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.auction.model.AuctionResult;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.AuctionStatus;
import vn.ptit.btl16.server.auction.model.BidRecord;
import vn.ptit.btl16.server.auction.model.Product;
import vn.ptit.btl16.server.auction.model.ProductImage;

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
    private final List<vn.ptit.btl16.server.auction.model.RoomProgress.Event> events = new ArrayList<>();
    private long eventId;
    private synchronized void event(long room, String type, Long user, String name, BigDecimal amount, String detail, Instant time) {
        events.add(new vn.ptit.btl16.server.auction.model.RoomProgress.Event(++eventId, room, type, user, name, amount, detail, time));
    }
    @Override public synchronized void appendPresence(long room, String type, long user, Instant time) {
        event(room, type, user, "", null, "", time);
    }
    @Override public synchronized List<vn.ptit.btl16.server.auction.model.RoomProgress.Event> findRoomEvents(long room, long before, long after, int limit) {
        var order = Comparator.comparingLong(vn.ptit.btl16.server.auction.model.RoomProgress.Event::id);
        return events.stream().filter(value -> value.auctionId() == room && value.id() < before && value.id() > after)
                .sorted(after > 0 ? order : order.reversed()).limit(limit).toList();
    }
    @Override public synchronized vn.ptit.btl16.server.auction.model.RoomProgress.Stats roomStats(long room) {
        var bids = findRecentBids(room, Integer.MAX_VALUE);
        var roomEvents = events.stream().filter(value -> value.auctionId() == room).toList();
        var auction = auctions.get(room);
        return new vn.ptit.btl16.server.auction.model.RoomProgress.Stats(bids.size(), bids.stream().map(BidRecord::getUserId).distinct().count(),
                roomEvents.stream().filter(value -> value.type().startsWith("EXTENDED_")).count(),
                roomEvents.stream().anyMatch(value -> value.type().equals("CREATED")),
                auction.getCurrentPrice().subtract(auction.getStartPrice()), bids.isEmpty() ? 0 : bids.get(0).getServerSequence());
    }
    @Override public synchronized List<vn.ptit.btl16.server.auction.model.RoomProgress.HistoryBid> bidPage(long room, long before, long through, Long user, int limit) {
        var bids = findRecentBids(room, Integer.MAX_VALUE);
        return bids.stream().filter(bid -> bid.getServerSequence() < before && bid.getServerSequence() <= through && (user == null || bid.getUserId() == user))
                .limit(limit).map(bid -> new vn.ptit.btl16.server.auction.model.RoomProgress.HistoryBid(bid,
                        bid.getAmount().subtract(bids.stream().filter(previous -> previous.getServerSequence() < bid.getServerSequence())
                                .findFirst().map(BidRecord::getAmount).orElse(auctions.get(room).getStartPrice())))).toList();
    }
    private final ConcurrentHashMap<Long, Product> products = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ProductImage> productImages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AuctionSnapshot> auctions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<BidRecord>> bidsByAuction = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AuctionResult> results = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<Long>> blockedUsersByAuction = new ConcurrentHashMap<>();
    private final AtomicLong productIds = new AtomicLong();
    private final AtomicLong auctionIds = new AtomicLong();
    private final AtomicLong bidIds = new AtomicLong();

    private final Map<Long, List<ProductMedia>> mediaByProduct = new ConcurrentHashMap<>();
    private final Map<String, Map<String, byte[]>> mediaPayloads = new ConcurrentHashMap<>();

    @Override public vn.ptit.btl16.server.media.MediaContent mediaContent(String id, String variant) throws java.io.IOException {
        byte[] bytes = mediaPayloads.getOrDefault(id, Map.of()).get(variant);
        if (bytes == null) { throw new java.io.IOException("Missing media variant"); }
        return vn.ptit.btl16.server.media.MediaContent.memory(bytes);
    }

    @Override public long mediaBytes() {
        return mediaPayloads.values().stream().flatMap(content -> content.values().stream()).mapToLong(bytes -> bytes.length).sum();
    }

    @Override public synchronized Product saveMediaProduct(MediaProductCommit commit) {
        ProductRequirements.validate(commit.description(), commit.media());
        Product current = commit.productId() == 0 ? null : products.get(commit.productId());
        if (commit.productId() != 0 && (current == null || current.getOwnerId() != commit.ownerId())) { throw new AuctionException(ErrorCode.AUCTION_FORBIDDEN, "Not owner"); }
        if (current != null && hasAuctionsForProduct(commit.productId())) { throw new AuctionException(ErrorCode.PRODUCT_FROZEN, "Frozen product"); }
        Optional<Product> duplicate = findProductByCode(commit.code());
        if (duplicate.isPresent() && duplicate.get().getProductId() != commit.productId()) { throw new AuctionException(ErrorCode.PRODUCT_CODE_EXISTS, "Duplicate code"); }
        for (ProductMedia media : commit.media()) {
            var existing = findMedia(media.id());
            if (existing.isPresent() && (commit.productId() == 0 || existing.get().productId() != commit.productId())) { throw new AuctionException(ErrorCode.AUCTION_FORBIDDEN, "Media belongs to other product"); }
        }
        long id = current == null ? productIds.incrementAndGet() : current.getProductId();
        ProductMedia cover = commit.media().stream().filter(ProductMedia::image).findFirst().orElseThrow();
        Instant now = Instant.now();
        Product product = new Product(id, commit.ownerId(), commit.ownerUsername(), commit.code(), commit.name(), commit.description(),
                current == null || current.isActive(), current == null ? now : current.getCreatedAt(), now, true, "image/jpeg", cover.name(),
                (int) cover.bytes(), current == null ? 1 : current.getImageVersion() + 1);
        List<ProductMedia> attached = new ArrayList<>();
        for (ProductMedia media : commit.media()) { attached.add(media.attached(id, attached.size())); }
        for (ProductMedia media : attached) {
            Map<String, byte[]> content = commit.content().get(media.id());
            if (content != null) { mediaPayloads.put(media.id(), content); }
        }
        for (ProductMedia media : findProductMedia(id)) {
            if (attached.stream().noneMatch(item -> item.id().equals(media.id()))) { mediaPayloads.remove(media.id()); }
        }
        products.put(id, product); mediaByProduct.put(id, List.copyOf(attached)); return product;
    }
    @Override public synchronized List<ProductMedia> findProductMedia(long productId) { return mediaByProduct.getOrDefault(productId, List.of()); }
    @Override public synchronized Optional<ProductMedia> findMedia(String id) { return mediaByProduct.values().stream().flatMap(List::stream).filter(media -> media.id().equals(id)).findFirst(); }
    @Override public synchronized List<Long> findLegacyImageProductIds() { return productImages.keySet().stream().filter(id -> findProductMedia(id).isEmpty()).toList(); }
    @Override public synchronized void migrateLegacyImage(long id, long version, ProductMedia media, Map<String, byte[]> content) {
        Product product = products.get(id);
        if (product != null && product.getImageVersion() == version && findProductMedia(id).isEmpty()) {
            mediaByProduct.put(id, List.of(media.attached(id, 0))); mediaPayloads.put(media.id(), content);
        }
    }

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
                true, commit.getCreatedAt(), commit.getCreatedAt(), commit.hasImage(),
                commit.getImageMime(), commit.getImageName(),
                commit.hasImage() ? commit.getImageData().length : 0,
                commit.hasImage() ? 1L : 0L);
        products.put(productId, product);
        if (commit.hasImage()) {
            productImages.put(productId, new ProductImage(
                    productId, commit.getImageData(), commit.getImageMime(), commit.getImageName(), 1L));
        }
        return product;
    }

    @Override
    public synchronized Product updateProduct(UpdateProductCommit commit) {
        if (hasAuctionsForProduct(commit.getProductId())) {
            throw new vn.ptit.btl16.server.auction.service.AuctionException(
                    vn.ptit.btl16.common.protocol.ErrorCode.PRODUCT_FROZEN, "Product is frozen");
        }
        Product current = products.get(commit.getProductId());
        if (current == null || current.getOwnerId() != commit.getOwnerId()) {
            throw new AuctionRepositoryException("Product was not found for owner");
        }
        Optional<Product> duplicate = findProductByCode(commit.getCode());
        if (duplicate.isPresent() && duplicate.get().getProductId() != commit.getProductId()) {
            throw new AuctionRepositoryException("Product code already exists");
        }
        long imageVersion = current.getImageVersion();
        boolean hasImage = current.hasImage();
        String imageMime = current.getImageMime();
        String imageName = current.getImageName();
        int imageSize = current.getImageSize();
        if (commit.isReplaceImage()) {
            imageVersion++;
            hasImage = true;
            imageMime = commit.getImageMime();
            imageName = commit.getImageName();
            imageSize = commit.getImageData().length;
            productImages.put(current.getProductId(), new ProductImage(
                    current.getProductId(), commit.getImageData(), imageMime, imageName, imageVersion));
        }
        Product updated = new Product(
                current.getProductId(), current.getOwnerId(), current.getOwnerUsername(),
                commit.getCode(), commit.getName(), commit.getDescription(),
                current.isActive(), current.getCreatedAt(), commit.getUpdatedAt(), hasImage,
                imageMime, imageName, imageSize, imageVersion);
        products.put(updated.getProductId(), updated);
        return updated;
    }

    @Override
    public synchronized Optional<Product> deactivateProduct(long productId, long ownerId) {
        Product current = products.get(productId);
        if (current == null || current.getOwnerId() != ownerId) {
            return Optional.empty();
        }
        if (hasOpenAuctionForProduct(productId)) {
            throw new AuctionException(ErrorCode.PRODUCT_IN_USE, "Product has an open auction");
        }
        Product updated = new Product(
                current.getProductId(), current.getOwnerId(), current.getOwnerUsername(),
                current.getCode(), current.getName(), current.getDescription(),
                false, current.getCreatedAt(), Instant.now(), current.hasImage(),
                current.getImageMime(), current.getImageName(), current.getImageSize(),
                current.getImageVersion());
        products.put(productId, updated);
        return Optional.of(updated);
    }

    @Override
    public synchronized Product restoreProduct(long productId, long ownerId) {
        Product current = products.get(productId);
        if (current == null || current.getOwnerId() != ownerId) {
            throw new AuctionException(ErrorCode.AUCTION_FORBIDDEN, "Product does not belong to this user");
        }
        if (current.isActive()) {
            return current;
        }
        if (hasOpenAuctionForProduct(productId)) {
            throw new AuctionException(ErrorCode.PRODUCT_IN_USE, "Product has an open auction");
        }
        if (hasSoldAuctionForProduct(productId)) {
            throw new AuctionException(ErrorCode.PRODUCT_ALREADY_SOLD, "Product already has a winner");
        }
        Product restored = new Product(
                current.getProductId(), current.getOwnerId(), current.getOwnerUsername(),
                current.getCode(), current.getName(), current.getDescription(),
                true, current.getCreatedAt(), Instant.now(), current.hasImage(),
                current.getImageMime(), current.getImageName(), current.getImageSize(),
                current.getImageVersion());
        products.put(productId, restored);
        return restored;
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
    public Optional<ProductImage> findProductImage(long productId) {
        return Optional.ofNullable(productImages.get(productId));
    }

    @Override
    public boolean hasOpenAuctionForProduct(long productId) {
        return auctions.values().stream().anyMatch(value ->
                value.getProduct().getProductId() == productId
                        && value.getStatus() == AuctionStatus.OPEN);
    }

    @Override
    public boolean hasAuctionsForProduct(long productId) {
        return auctions.values().stream().anyMatch(value -> value.getProduct().getProductId() == productId);
    }

    @Override
    public boolean hasSoldAuctionForProduct(long productId) {
        return auctions.values().stream().anyMatch(value -> value.getProduct().getProductId() == productId
                && value.getStatus() == AuctionStatus.ENDED && value.getCurrentWinnerId() != null);
    }

    @Override
    public synchronized AuctionSnapshot createAuction(CreateAuctionCommit commit) {
        Product product = products.get(commit.getProductId());
        if (product == null || product.getOwnerId() != commit.getHostUserId()) {
            throw new AuctionException(ErrorCode.AUCTION_FORBIDDEN, "Product does not belong to this user");
        }
        if (!product.isActive()) {
            throw new AuctionException(ErrorCode.PRODUCT_INACTIVE, "Product is inactive");
        }
        if (hasOpenAuctionForProduct(commit.getProductId())) {
            throw new AuctionException(ErrorCode.AUCTION_ALREADY_EXISTS, "Product has an open auction");
        }
        if (hasSoldAuctionForProduct(commit.getProductId())) {
            throw new AuctionException(ErrorCode.PRODUCT_ALREADY_SOLD, "Product already has a winner");
        }
        ProductRequirements.validate(product.getDescription(), findProductMedia(product.getProductId()));
        long auctionId = auctionIds.incrementAndGet();
        AuctionSnapshot snapshot = new AuctionSnapshot(
                auctionId, product, commit.getHostUserId(), commit.getHostUsername(),
                commit.getStartPrice(), commit.getMinBidIncrement(), commit.getStartPrice(),
                null, "", commit.getStartTime(), commit.getEndTime(),
                AuctionStatus.OPEN, null, 0L, commit.getVisibility(),
                commit.getRoomPasswordHash(), commit.getRoomPasswordSalt(),
                commit.getRoomPasswordIterations() == null ? 0 : commit.getRoomPasswordIterations(),
                commit.getEndTime().plusSeconds(vn.ptit.btl16.server.auction.service.AuctionRules.MAX_AUTO_EXTENSION_SECONDS), commit.hasAnonymousGuests());
        addAuction(snapshot);
        event(auctionId, "CREATED", commit.getHostUserId(), commit.getHostUsername(), null, "", Instant.now());
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
        if (commit.getBidderId() == current.getHostUserId()
                || Long.valueOf(commit.getBidderId()).equals(current.getCurrentWinnerId())
                || commit.getAmount().compareTo(current.getCurrentPrice().add(current.getMinBidIncrement())) < 0
                || commit.getCreatedAt().isBefore(current.getStartTime())
                || commit.getNewEndTime().isBefore(current.getEndTime())
                || commit.getNewEndTime().isAfter(current.getHardEndTime())
                || isAuctionUserBlocked(commit.getAuctionId(), commit.getBidderId())) {
            throw new AuctionConflictException("Bid violates auction rules");
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
        event(commit.getAuctionId(), "BID_ACCEPTED", commit.getBidderId(), commit.getBidderUsername(), commit.getAmount(), "", commit.getCreatedAt());
        event(commit.getAuctionId(), "LEADER_CHANGED", commit.getBidderId(), commit.getBidderUsername(), commit.getAmount(), "", commit.getCreatedAt());
        if (commit.getNewEndTime().isAfter(current.getEndTime())) {
            event(commit.getAuctionId(), "EXTENDED_AUTO", null, "", null, commit.getNewEndTime().toString(), commit.getCreatedAt());
        }
        return bid;
    }

    @Override
    public synchronized boolean extendAuction(ExtendAuctionCommit commit) {
        AuctionSnapshot current = auctions.get(commit.getAuctionId());
        if (current == null || current.getStatus() != AuctionStatus.OPEN
                || hasBids(commit.getAuctionId())
                || commit.getNewEndTime().isAfter(current.getHardEndTime())
                || !current.getEndTime().equals(commit.getExpectedEndTime())) {
            return false;
        }
        auctions.put(current.getAuctionId(), copyAuction(
                current, current.getCurrentPrice(), current.getCurrentWinnerId(),
                current.getCurrentWinnerUsername(), commit.getNewEndTime(),
                current.getStatus(), current.getEndedAt()));
        event(commit.getAuctionId(), "EXTENDED_MANUAL", null, "", null, commit.getNewEndTime().toString(), Instant.now());
        return true;
    }

    @Override
    public synchronized Optional<AuctionResult> closeAuction(CloseAuctionCommit commit) {
        AuctionSnapshot current = auctions.get(commit.getAuctionId());
        if (current == null || current.getStatus() != AuctionStatus.OPEN) {
            return Optional.empty();
        }
        if (!commit.isRequireExpired() && hasBids(commit.getAuctionId())) {
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
        event(commit.getAuctionId(), "ENDED", commit.getWinnerId(), commit.getWinnerUsername(), commit.getFinalPrice(), "", commit.getEndedAt());
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
        event(commit.getAuctionId(), "CANCELLED", null, "", null, "", commit.getCancelledAt());
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
                status, endedAt, current.getVersion() + 1, current.getVisibility(),
                current.getRoomPasswordHash(), current.getRoomPasswordSalt(),
                current.getRoomPasswordIterations(), current.getHardEndTime(), current.hasAnonymousGuests());
    }

    @Override
    public boolean hasBids(long auctionId) {
        List<BidRecord> values = bidsByAuction.get(auctionId);
        return values != null && !values.isEmpty();
    }

    @Override
    public boolean hasBidsByUser(long auctionId, long userId) {
        List<BidRecord> values = bidsByAuction.get(auctionId);
        if (values == null) {
            return false;
        }
        synchronized (values) {
            return values.stream().anyMatch(bid -> bid.getUserId() == userId);
        }
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
