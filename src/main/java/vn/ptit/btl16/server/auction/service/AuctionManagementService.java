package vn.ptit.btl16.server.auction.service;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.server.auction.model.AuctionResult;
import vn.ptit.btl16.server.auction.model.AuctionRuntime;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.AuctionStatus;
import vn.ptit.btl16.server.auction.model.Product;
import vn.ptit.btl16.server.auction.repository.BlockAuctionUserCommit;
import vn.ptit.btl16.server.auction.repository.CancelAuctionCommit;
import vn.ptit.btl16.server.auction.repository.CloseAuctionCommit;
import vn.ptit.btl16.server.auction.repository.CreateAuctionCommit;
import vn.ptit.btl16.server.auction.repository.CreateProductCommit;
import vn.ptit.btl16.server.auction.repository.ExtendAuctionCommit;
import vn.ptit.btl16.server.auction.repository.AuctionRepository;
import vn.ptit.btl16.server.auction.repository.UpdateProductCommit;
import vn.ptit.btl16.server.session.UserSession;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class AuctionManagementService {
    private static final Pattern PRODUCT_CODE = Pattern.compile("[A-Z0-9_-]{2,30}");
    private static final BigDecimal MAX_MONEY = new BigDecimal("999999999999999.00");
    private static final int MIN_DURATION_MINUTES = 1;
    private static final int MAX_DURATION_MINUTES = 180;
    private static final int MIN_EXTENSION_SECONDS = 10;
    private static final int MAX_EXTENSION_SECONDS = 600;

    private final AuctionManager auctions;
    private final AuctionRepository repository;
    private final RoomManager rooms;
    private final AuctionBroadcastService broadcasts;

    public AuctionManagementService(
            AuctionManager auctions,
            AuctionRepository repository,
            RoomManager rooms,
            AuctionBroadcastService broadcasts) {
        this.auctions = auctions;
        this.repository = repository;
        this.rooms = rooms;
        this.broadcasts = broadcasts;
    }

    public Product createProduct(
            UserSession session,
            String rawCode,
            String rawName,
            String rawDescription) {
        String code = cleanCode(rawCode);
        String name = cleanName(rawName);
        String description = cleanDescription(rawDescription);
        if (repository.findProductByCode(code).isPresent()) {
            throw new AuctionException(ErrorCode.PRODUCT_CODE_EXISTS, "Ma san pham da ton tai");
        }
        Instant now = Instant.now();
        return repository.createProduct(new CreateProductCommit(
                session.getUserId(), session.getUsername(), code, name, description, now));
    }

    public List<Product> myProducts(UserSession session) {
        return repository.findProductsByOwner(session.getUserId());
    }

    public Product updateProduct(
            UserSession session,
            long productId,
            String rawCode,
            String rawName,
            String rawDescription) {
        Product current = requireOwnedProduct(session, productId);
        if (repository.hasOpenAuctionForProduct(productId)) {
            throw new AuctionException(
                    ErrorCode.PRODUCT_IN_USE,
                    "Khong the sua san pham dang co phien dau gia mo");
        }
        String code = cleanCode(rawCode);
        Optional<Product> duplicate = repository.findProductByCode(code);
        if (duplicate.isPresent() && duplicate.get().getProductId() != current.getProductId()) {
            throw new AuctionException(ErrorCode.PRODUCT_CODE_EXISTS, "Ma san pham da ton tai");
        }
        return repository.updateProduct(new UpdateProductCommit(
                productId,
                session.getUserId(),
                code,
                cleanName(rawName),
                cleanDescription(rawDescription),
                Instant.now()));
    }

    public Product deactivateProduct(UserSession session, long productId) {
        requireOwnedProduct(session, productId);
        if (repository.hasOpenAuctionForProduct(productId)) {
            throw new AuctionException(
                    ErrorCode.PRODUCT_IN_USE,
                    "Khong the vo hieu hoa san pham dang co phien dau gia mo");
        }
        return repository.deactivateProduct(productId, session.getUserId())
                .orElseThrow(() -> new AuctionException(
                        ErrorCode.PRODUCT_NOT_FOUND,
                        "Khong tim thay san pham"));
    }

    public AuctionSnapshot createAuction(
            UserSession session,
            long productId,
            BigDecimal rawStartPrice,
            BigDecimal rawMinIncrement,
            int durationMinutes) {
        Product product = requireOwnedProduct(session, productId);
        if (!product.isActive()) {
            throw new AuctionException(ErrorCode.PRODUCT_INACTIVE, "San pham da bi vo hieu hoa");
        }
        if (repository.hasOpenAuctionForProduct(productId)) {
            throw new AuctionException(
                    ErrorCode.AUCTION_ALREADY_EXISTS,
                    "San pham da co phien dau gia dang mo");
        }
        if (durationMinutes < MIN_DURATION_MINUTES || durationMinutes > MAX_DURATION_MINUTES) {
            throw new AuctionException(
                    ErrorCode.INVALID_DURATION,
                    "Thoi luong phai tu 1 den 180 phut");
        }
        BigDecimal startPrice = normalizeMoney(rawStartPrice, ErrorCode.INVALID_AMOUNT);
        BigDecimal minIncrement = normalizeMoney(rawMinIncrement, ErrorCode.INVALID_INCREMENT);
        Instant startTime = Instant.now();
        AuctionSnapshot snapshot = repository.createAuction(new CreateAuctionCommit(
                session.getUserId(),
                session.getUsername(),
                productId,
                startPrice,
                minIncrement,
                startTime,
                startTime.plusSeconds(durationMinutes * 60L)));
        auctions.addRuntime(snapshot);
        broadcasts.broadcastAll(
                MessageType.AUCTION_CREATED,
                AuctionWireData.snapshot(snapshot, List.of(), 0, Instant.now()));
        return snapshot;
    }

    public List<AuctionSnapshot> myAuctions(UserSession session) {
        return auctions.snapshotsByHost(session.getUserId());
    }

    public AuctionSnapshot joinAuction(
            UserSession session,
            String connectionId,
            long auctionId) {
        AuctionSnapshot snapshot = auctions.requireRuntime(auctionId).snapshot();
        if (repository.isAuctionUserBlocked(auctionId, session.getUserId())) {
            throw new AuctionException(
                    ErrorCode.USER_BLOCKED_FROM_AUCTION,
                    "Ban da bi chu tri moi khoi phong nay");
        }
        rooms.join(
                auctionId,
                connectionId,
                session.getUserId(),
                session.getUsername());
        return snapshot;
    }

    public AuctionSnapshot extendAuction(
            UserSession session,
            long auctionId,
            int extensionSeconds) {
        if (extensionSeconds < MIN_EXTENSION_SECONDS || extensionSeconds > MAX_EXTENSION_SECONDS) {
            throw new AuctionException(
                    ErrorCode.INVALID_DURATION,
                    "Thoi gian gia han phai tu 10 den 600 giay");
        }
        AuctionRuntime runtime = auctions.requireRuntime(auctionId);
        AuctionSnapshot snapshot;
        runtime.getLock().lock();
        try {
            requireHost(session, runtime);
            requireOpen(runtime);
            Instant oldEndTime = runtime.getEndTimeUnsafe();
            Instant newEndTime = oldEndTime.plusSeconds(extensionSeconds);
            if (!repository.extendAuction(new ExtendAuctionCommit(
                    auctionId, oldEndTime, newEndTime))) {
                throw new AuctionException(
                        ErrorCode.DATABASE_CONFLICT,
                        "Trang thai phien da thay doi, hay tai lai");
            }
            runtime.applyExtended(newEndTime);
            snapshot = runtime.snapshot();
        } finally {
            runtime.getLock().unlock();
        }
        broadcasts.broadcastToRoom(
                auctionId,
                MessageType.AUCTION_EXTENDED,
                AuctionWireData.extended(snapshot, extensionSeconds, "HOST", Instant.now()));
        return snapshot;
    }

    public AuctionResult endAuction(UserSession session, long auctionId) {
        AuctionRuntime runtime = auctions.requireRuntime(auctionId);
        AuctionResult result;
        AuctionSnapshot ended;
        runtime.getLock().lock();
        try {
            requireHost(session, runtime);
            requireOpen(runtime);
            Instant now = Instant.now();
            result = repository.closeAuction(new CloseAuctionCommit(
                    auctionId,
                    runtime.getCurrentWinnerIdUnsafe(),
                    runtime.getCurrentWinnerUsernameUnsafe(),
                    runtime.getCurrentPriceUnsafe(),
                    runtime.getEndTimeUnsafe(),
                    now,
                    false)).orElseThrow(() -> new AuctionException(
                            ErrorCode.DATABASE_CONFLICT,
                            "Phien da duoc ket thuc boi yeu cau khac"));
            runtime.markEnded(now);
            ended = runtime.snapshot();
        } finally {
            runtime.getLock().unlock();
        }
        broadcasts.broadcastToRoom(
                auctionId,
                MessageType.AUCTION_ENDED,
                AuctionWireData.ended(ended, result, Instant.now()));
        return result;
    }

    public AuctionSnapshot cancelAuction(UserSession session, long auctionId) {
        AuctionRuntime runtime = auctions.requireRuntime(auctionId);
        AuctionSnapshot cancelled;
        runtime.getLock().lock();
        try {
            requireHost(session, runtime);
            requireOpen(runtime);
            if (repository.hasBids(auctionId)) {
                throw new AuctionException(
                        ErrorCode.AUCTION_HAS_BIDS,
                        "Khong the huy phien da co nguoi dat gia");
            }
            Instant now = Instant.now();
            if (!repository.cancelAuction(new CancelAuctionCommit(auctionId, now))) {
                throw new AuctionException(
                        ErrorCode.DATABASE_CONFLICT,
                        "Khong the huy phien do trang thai da thay doi");
            }
            runtime.markCancelled(now);
            cancelled = runtime.snapshot();
        } finally {
            runtime.getLock().unlock();
        }
        broadcasts.broadcastToRoom(
                auctionId,
                MessageType.AUCTION_CANCELLED,
                AuctionWireData.cancelled(cancelled, Instant.now()));
        return cancelled;
    }

    public KickOutcome kickUser(
            UserSession session,
            long auctionId,
            String rawUsername) {
        AuctionRuntime runtime = auctions.requireRuntime(auctionId);
        String username = rawUsername == null ? "" : rawUsername.trim();
        if (username.isBlank()) {
            throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Can nhap username can moi");
        }
        KickOutcome outcome;
        runtime.getLock().lock();
        try {
            requireHost(session, runtime);
            requireOpen(runtime);
            RoomMember member = rooms.findByUsername(auctionId, username)
                    .orElseThrow(() -> new AuctionException(
                            ErrorCode.USER_NOT_IN_AUCTION_ROOM,
                            "Nguoi dung khong co trong phong"));
            if (member.getUserId() == runtime.getHostUserId()) {
                throw new AuctionException(
                        ErrorCode.CANNOT_KICK_HOST,
                        "Khong the moi chu tri khoi phong");
            }
            repository.blockAuctionUser(new BlockAuctionUserCommit(
                    auctionId, member.getUserId(), session.getUserId(), Instant.now()));
            Set<String> removedConnections = rooms.kickUser(auctionId, member.getUserId());
            outcome = new KickOutcome(member.getUserId(), member.getUsername(), removedConnections);
        } finally {
            runtime.getLock().unlock();
        }
        broadcasts.notifyConnections(
                outcome.getConnectionIds(),
                MessageType.AUCTION_KICKED,
                AuctionWireData.kicked(auctionId, outcome.getUsername(), Instant.now()));
        return outcome;
    }

    private Product requireOwnedProduct(UserSession session, long productId) {
        Product product = repository.findProductById(productId)
                .orElseThrow(() -> new AuctionException(
                        ErrorCode.PRODUCT_NOT_FOUND,
                        "Khong tim thay san pham"));
        if (product.getOwnerId() != session.getUserId()) {
            throw new AuctionException(
                    ErrorCode.AUCTION_FORBIDDEN,
                    "Ban khong phai chu so huu san pham");
        }
        return product;
    }

    private void requireHost(UserSession session, AuctionRuntime runtime) {
        if (runtime.getHostUserId() != session.getUserId()) {
            throw new AuctionException(
                    ErrorCode.AUCTION_FORBIDDEN,
                    "Chi chu tri moi duoc thuc hien thao tac nay");
        }
    }

    private void requireOpen(AuctionRuntime runtime) {
        if (runtime.getStatusUnsafe() != AuctionStatus.OPEN
                || !Instant.now().isBefore(runtime.getEndTimeUnsafe())) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_OPEN, "Phien dau gia khong con mo");
        }
    }

    private String cleanCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (!PRODUCT_CODE.matcher(code).matches()) {
            throw new AuctionException(
                    ErrorCode.VALIDATION_ERROR,
                    "Ma san pham phai co 2-30 ky tu A-Z, 0-9, _ hoac -");
        }
        return code;
    }

    private String cleanName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank() || name.length() > 150) {
            throw new AuctionException(
                    ErrorCode.VALIDATION_ERROR,
                    "Ten san pham phai co 1-150 ky tu");
        }
        return name;
    }

    private String cleanDescription(String rawDescription) {
        String description = rawDescription == null ? "" : rawDescription.trim();
        if (description.length() > 2000) {
            throw new AuctionException(
                    ErrorCode.VALIDATION_ERROR,
                    "Mo ta san pham toi da 2000 ky tu");
        }
        return description;
    }

    private BigDecimal normalizeMoney(BigDecimal value, ErrorCode errorCode) {
        if (value == null) {
            throw new AuctionException(errorCode, "Gia tri tien khong hop le");
        }
        BigDecimal normalized;
        try {
            normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new AuctionException(errorCode, "Gia tri tien chi duoc co toi da 2 so thap phan");
        }
        if (normalized.signum() <= 0 || normalized.compareTo(MAX_MONEY) > 0) {
            throw new AuctionException(errorCode, "Gia tri tien nam ngoai khoang cho phep");
        }
        return normalized;
    }
}
