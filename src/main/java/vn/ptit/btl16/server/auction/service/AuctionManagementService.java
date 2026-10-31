package vn.ptit.btl16.server.auction.service;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.server.media.*;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.ProductRestorationStatus;
import vn.ptit.btl16.server.auction.model.AuctionResult;
import vn.ptit.btl16.server.auction.model.AuctionRuntime;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.AuctionStatus;
import vn.ptit.btl16.server.auction.model.Product;
import vn.ptit.btl16.server.auction.model.ProductImage;
import vn.ptit.btl16.server.auction.model.RoomVisibility;
import vn.ptit.btl16.server.auction.repository.BlockAuctionUserCommit;
import vn.ptit.btl16.server.auction.repository.CancelAuctionCommit;
import vn.ptit.btl16.server.auction.repository.CloseAuctionCommit;
import vn.ptit.btl16.server.auction.repository.CreateAuctionCommit;
import vn.ptit.btl16.server.auction.repository.ExtendAuctionCommit;
import vn.ptit.btl16.server.auction.repository.AuctionRepository;
import vn.ptit.btl16.server.session.UserSession;
import vn.ptit.btl16.server.session.SessionManager;
import vn.ptit.btl16.server.account.security.PasswordHash;
import vn.ptit.btl16.server.account.security.PasswordHasher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Arrays;

public final class AuctionManagementService {
    private static final int MIN_DURATION_MINUTES = AuctionRules.MIN_DURATION_MINUTES;
    private static final int MAX_DURATION_MINUTES = AuctionRules.MAX_DURATION_MINUTES;
    private static final int MIN_EXTENSION_SECONDS = AuctionRules.MIN_EXTENSION_SECONDS;
    private static final int MAX_EXTENSION_SECONDS = AuctionRules.MAX_EXTENSION_SECONDS;

    private final AuctionManager auctions;
    private final AuctionRepository repository;
    private final RoomManager rooms;
    private final AuctionBroadcastService broadcasts;
    private final PasswordHasher passwordHasher;
    private final SessionManager sessions;
    private MediaService media;

    public void setMediaService(MediaService value) { media = value; }

    public synchronized MediaService media() {
        if (media == null) { media = new MediaService(MediaConfig.load(true), repository, sessions, this); }
        return media;
    }

    public AuctionManagementService(
            AuctionManager auctions,
            AuctionRepository repository,
            RoomManager rooms,
            AuctionBroadcastService broadcasts,
            PasswordHasher passwordHasher,
            SessionManager sessions) {
        this.auctions = auctions;
        this.repository = repository;
        this.rooms = rooms;
        this.broadcasts = broadcasts;
        this.passwordHasher = passwordHasher;
        this.sessions = sessions;
        rooms.setOccupancyListener((auctionId, count, version) -> broadcasts.broadcastAll(
                MessageType.ROOM_OCCUPANCY_CHANGED, java.util.Map.of(
                        "auctionId", Long.toString(auctionId), "watcherCount", Integer.toString(count),
                        "occupancyVersion", Long.toString(version), "serverNow", Instant.now().toString())));
    }

    public Product createProduct(UserSession session, String code, String name, String description) {
        return createProduct(session, code, name, description, null, null, null);
    }

    public Product createProduct(UserSession session, String code, String name, String description,
                                 byte[] imageData, String imageMime, String imageName) {
        return media().legacySave(session, 0, code, name, description, imageData, imageName);
    }

    public List<Product> myProducts(UserSession session) {
        requireLoggedIn(session);
        var products = new java.util.LinkedHashMap<Long, Product>();
        repository.findPurchasedProducts(session.getUserId()).forEach(product -> products.put(product.getProductId(), product));
        repository.findProductsByOwner(session.getUserId()).forEach(product -> products.put(product.getProductId(), product));
        return List.copyOf(products.values());
    }

    public java.util.Set<Long> anonymousPurchasedProducts(UserSession session) {
        requireLoggedIn(session); return repository.anonymousPurchasedProducts(session.getUserId());
    }

    public vn.ptit.btl16.common.protocol.ProductTransactionStatus transactionStatus(Product product) {
        return repository.productTransactionStatus(product.getProductId());
    }

    public vn.ptit.btl16.common.protocol.ProductTransactionStatus transactionStatus(UserSession viewer, Product product) {
        return product.getOwnerId() == viewer.getUserId() ? transactionStatus(product)
                : vn.ptit.btl16.common.protocol.ProductTransactionStatus.OWNED;
    }

    public Product updateProduct(UserSession session, long productId, String code, String name, String description) {
        return media().legacySave(session, productId, code, name, description, null, null);
    }

    public Product updateProduct(UserSession session, long productId, String code, String name, String description,
                                 byte[] imageData, String imageMime, String imageName) {
        return media().legacySave(session, productId, code, name, description, imageData, imageName);
    }

    public Product saveMediaProduct(UserSession session, long productId, String code, String name,
                                    String description, List<ProductMedia> selected) {
        return repository.saveMediaProduct(prepareMediaProduct(session, productId, code, name, description, selected));
    }

    public MediaProductCommit prepareMediaProduct(UserSession session, long productId, String code, String name,
                                                  String description, List<ProductMedia> selected) {
        requireMediaEdit(session, productId);
        String assignedCode = productId > 0 ? requireOwnedProduct(session, productId).getCode() : "";
        ProductRequirements.validate(description, selected);
        return new MediaProductCommit(productId, session.getUserId(), session.getUsername(),
                assignedCode, cleanName(name), cleanDescription(description), selected);
    }

    public Product saveMediaProduct(UserSession session, long productId, String code, String name,
                                    String description, List<ProductMedia> selected, java.util.Map<String, java.util.Map<String, byte[]>> content) {
        MediaProductCommit prepared = prepareMediaProduct(session, productId, code, name, description, selected);
        return repository.saveMediaProduct(new MediaProductCommit(prepared.productId(), prepared.ownerId(), prepared.ownerUsername(),
                prepared.code(), prepared.name(), prepared.description(), prepared.media(), content));
    }

    public void requireMediaEdit(UserSession session, long productId) {
        requireLoggedIn(session);
        if (productId == 0) { return; }
        requireOwnedProduct(session, productId);
        if (repository.hasAuctionsForProduct(productId)) {
            throw new AuctionException(ErrorCode.PRODUCT_FROZEN, "Sản phẩm đã từng đấu giá: nội dung, thứ tự, ảnh đại diện và ảnh/video đã đóng băng");
        }
    }

    private void requireLoggedIn(UserSession session) {
        if (session == null || !session.isActive()) { throw new AuctionException(ErrorCode.AUTH_REQUIRED, "Cần đăng nhập"); }
    }

    public void requireMediaRead(UserSession session, long productId, long auctionId) {
        requireLoggedIn(session);
        Product product = repository.findProductById(productId).orElseThrow(() -> new AuctionException(
                ErrorCode.PRODUCT_NOT_FOUND, "Không tìm thấy sản phẩm"));
        boolean allowed = auctionId == 0 && (product.getOwnerId() == session.getUserId()
                || repository.hasPurchasedProduct(session.getUserId(), productId));
        if (!allowed) {
            allowed = (auctionId == 0 ? repository.findAllAuctions().stream() : repository.findAuctionById(auctionId).stream()).anyMatch(auction ->
                    auction.getProduct().getProductId() == productId && (auctionId == 0 || auction.getAuctionId() == auctionId)
                    && !repository.isAuctionUserBlocked(auction.getAuctionId(), session.getUserId())
                    && (auction.getVisibility() == RoomVisibility.PUBLIC || auction.isHostedBy(session.getUserId())
                        || sessions.hasPrivateRoomAccess(session, auction.getAuctionId())));
        }
        if (!allowed) { throw new AuctionException(ErrorCode.ROOM_ACCESS_DENIED, "Không có quyền xem ảnh/video trong phòng này"); }
    }

    public ProductImage getProductImage(UserSession session, long productId) {
        requireMediaRead(session, productId, 0);
        Product product = repository.findProductById(productId).orElseThrow();
        return new ProductImage(productId, media().cover(productId), "image/jpeg", "thumbnail.jpg", product.getImageVersion());
    }

    public Product deactivateProduct(UserSession session, long productId) {
        requireOwnedProduct(session, productId);
        if (repository.hasOpenAuctionForProduct(productId)) {
            throw new AuctionException(
                    ErrorCode.PRODUCT_IN_USE,
                    "Không thể vô hiệu hóa sản phẩm đang có phiên đấu giá mở");
        }
        return repository.deactivateProduct(productId, session.getUserId())
                .orElseThrow(() -> new AuctionException(
                        ErrorCode.PRODUCT_NOT_FOUND,
                        "Không tìm thấy sản phẩm"));
    }

    public void deleteProduct(UserSession session, long productId) {
        requireLoggedIn(session);
        requireOwnedProduct(session, productId);
        repository.deleteProduct(productId, session.getUserId());
    }

    public ProductRestorationStatus restorationStatus(Product product) {
        if (product.isActive()) {
            return ProductRestorationStatus.ACTIVE;
        }
        if (repository.hasOpenAuctionForProduct(product.getProductId())) {
            return ProductRestorationStatus.OPEN_AUCTION;
        }
        if (repository.hasSoldAuctionForProduct(product.getProductId())) {
            return ProductRestorationStatus.SOLD;
        }
        return ProductRestorationStatus.ELIGIBLE;
    }

    public Product restoreProduct(UserSession session, long productId) {
        Product product = requireOwnedProduct(session, productId);
        ProductRestorationStatus status = restorationStatus(product);
        if (status == ProductRestorationStatus.OPEN_AUCTION) {
            throw new AuctionException(ErrorCode.PRODUCT_IN_USE,
                    "Không thể khôi phục mặt hàng đang có phiên đấu giá mở");
        }
        if (status == ProductRestorationStatus.SOLD) {
            throw new AuctionException(ErrorCode.PRODUCT_ALREADY_SOLD,
                    "Mặt hàng đã có người thắng, không được khôi phục để đấu giá lại kể cả chưa thanh toán");
        }
        return repository.restoreProduct(productId, session.getUserId());
    }

    public AuctionSnapshot createAuction(
            UserSession session,
            long productId,
            BigDecimal rawStartPrice,
            BigDecimal rawMinIncrement,
            int durationMinutes) {
        Product product = requireOwnedProduct(session, productId);
        if (!product.isActive()) {
            throw new AuctionException(ErrorCode.PRODUCT_INACTIVE, "Sản phẩm đã bị vô hiệu hóa");
        }
        if (repository.hasOpenAuctionForProduct(productId)) {
            throw new AuctionException(
                    ErrorCode.AUCTION_ALREADY_EXISTS,
                    "Sản phẩm đã có phiên đấu giá đang mở");
        }
        if (durationMinutes < MIN_DURATION_MINUTES || durationMinutes > MAX_DURATION_MINUTES) {
            throw new AuctionException(
                    ErrorCode.INVALID_DURATION,
                    "Thời lượng phải từ 1 đến 180 phút");
        }
        BigDecimal startPrice = normalizeMoney(rawStartPrice, ErrorCode.INVALID_AMOUNT);
        BigDecimal minIncrement = normalizeMoney(rawMinIncrement, ErrorCode.INVALID_INCREMENT);
        AuctionRules.requireCompetitivePrice(startPrice, minIncrement);
        requireUnsoldProduct(productId);
        ProductRequirements.validate(product.getDescription(), repository.findProductMedia(productId));
        media().requireReady(productId);
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

    public AuctionSnapshot createAuction(
            UserSession session,
            long productId,
            BigDecimal rawStartPrice,
            BigDecimal rawMinIncrement,
            int durationMinutes,
            RoomVisibility visibility,
            String rawRoomPassword) {
        return createAuction(session, productId, rawStartPrice, rawMinIncrement, durationMinutes,
                visibility, rawRoomPassword, false);
    }

    public AuctionSnapshot createAuction(UserSession session, long productId, BigDecimal rawStartPrice,
            BigDecimal rawMinIncrement, int durationMinutes, RoomVisibility visibility,
            String rawRoomPassword, boolean anonymousGuests) {
        return createAuction(session, productId, rawStartPrice, rawMinIncrement, durationMinutes,
                visibility, rawRoomPassword, anonymousGuests, null);
    }

    public AuctionSnapshot createAuction(UserSession session, long productId, BigDecimal rawStartPrice,
            BigDecimal rawMinIncrement, int durationMinutes, RoomVisibility visibility,
            String rawRoomPassword, boolean anonymousGuests, Instant requestedStartTime) {
        return createAuction(session, productId, rawStartPrice, rawMinIncrement, durationMinutes,
                visibility, rawRoomPassword, anonymousGuests, requestedStartTime, 100,
                requestedStartTime == null ? BigDecimal.ZERO : BigDecimal.ONE);
    }

    public AuctionSnapshot createAuction(UserSession session, long productId, BigDecimal rawStartPrice,
            BigDecimal rawMinIncrement, int durationMinutes, RoomVisibility visibility,
            String rawRoomPassword, boolean anonymousGuests, Instant requestedStartTime,
            int maxParticipants, BigDecimal rawDepositAmount) {
        if (maxParticipants < 1 || maxParticipants > 10000) {
            throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Số lượng tối đa phải là số nguyên từ 1 đến 10000.");
        }
        BigDecimal depositAmount = BigDecimal.ZERO;
        if (requestedStartTime != null) {
            depositAmount = normalizeMoney(rawDepositAmount, ErrorCode.INVALID_AMOUNT);
        } else if (rawDepositAmount != null && rawDepositAmount.signum() != 0) {
            throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Chỉ phòng hẹn giờ mới thu tiền đặt cọc.");
        }
        Product product = requireOwnedProduct(session, productId);
        if (!product.isActive()) {
            throw new AuctionException(ErrorCode.PRODUCT_INACTIVE, "Sản phẩm đã bị vô hiệu hóa");
        }
        if (repository.hasOpenAuctionForProduct(productId)) {
            throw new AuctionException(ErrorCode.AUCTION_ALREADY_EXISTS,
                    "Sản phẩm đã có phiên đấu giá đang mở");
        }
        if (durationMinutes < MIN_DURATION_MINUTES || durationMinutes > MAX_DURATION_MINUTES) {
            throw new AuctionException(ErrorCode.INVALID_DURATION,
                    "Thời lượng phải từ 1 đến 180 phút");
        }
        RoomVisibility safeVisibility = visibility == null ? RoomVisibility.PUBLIC : visibility;
        PasswordHash passwordHash = null;
        char[] password = rawRoomPassword == null ? new char[0] : rawRoomPassword.toCharArray();
        try {
            if (safeVisibility == RoomVisibility.PRIVATE) {
                if (password.length < 4 || password.length > 100) {
                    throw new AuctionException(ErrorCode.ROOM_PASSWORD_REQUIRED,
                            "Mật khẩu phòng riêng phải có 4-100 ký tự");
                }
                passwordHash = passwordHasher.hash(password);
            }
        } finally {
            Arrays.fill(password, '\0');
        }
        BigDecimal startPrice = normalizeMoney(rawStartPrice, ErrorCode.INVALID_AMOUNT);
        BigDecimal minIncrement = normalizeMoney(rawMinIncrement, ErrorCode.INVALID_INCREMENT);
        AuctionRules.requireCompetitivePrice(startPrice, minIncrement);
        requireUnsoldProduct(productId);
        ProductRequirements.validate(product.getDescription(), repository.findProductMedia(productId));
        media().requireReady(productId);
        Instant now = Instant.now();
        if (requestedStartTime != null && (!requestedStartTime.isAfter(now)
                || requestedStartTime.isAfter(Instant.parse("9999-12-30T00:00:00Z")))) {
            throw new AuctionException(ErrorCode.VALIDATION_ERROR,
                    "Thời gian bắt đầu phải ở tương lai và là ngày giờ hợp lệ");
        }
        Instant startTime = requestedStartTime == null ? now : requestedStartTime;
        AuctionSnapshot snapshot = repository.createAuction(new CreateAuctionCommit(
                session.getUserId(), session.getUsername(), productId, startPrice, minIncrement,
                startTime, startTime.plusSeconds(durationMinutes * 60L), safeVisibility,
                passwordHash == null ? "" : passwordHash.getHashBase64(),
                passwordHash == null ? "" : passwordHash.getSaltBase64(),
                passwordHash == null ? null : passwordHash.getIterations(), anonymousGuests,
                maxParticipants, depositAmount));
        auctions.addRuntime(snapshot);
        broadcasts.broadcastAll(MessageType.AUCTION_CREATED,
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
        return joinAuction(session, connectionId, auctionId, "");
    }

    public AuctionSnapshot joinAuction(
            UserSession session,
            String connectionId,
            long auctionId,
            String roomPassword) {
        AuctionRuntime runtime = auctions.requireRuntime(auctionId);
        AuctionSnapshot snapshot = runtime.snapshot();
        if (repository.isAuctionUserBlocked(auctionId, session.getUserId())) {
            throw new AuctionException(
                    ErrorCode.USER_BLOCKED_FROM_AUCTION,
                    "Bạn đã bị chủ trì mời khỏi phòng này");
        }
        if (!snapshot.isOpenAt(Instant.now())) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_OPEN, "Phiên đấu giá không còn mở");
        }
        requireRoomPassword(session, snapshot, roomPassword);
        runtime.getLock().lock();
        try {
            requireOpen(runtime);
            requireNotBlocked(session, auctionId);
            if (!snapshot.isHostedBy(session.getUserId()) && snapshot.getDepositAmount().signum() > 0
                    && repository.auctionDeposit(auctionId, session.getUserId()).compareTo(snapshot.getDepositAmount()) < 0) {
                throw new AuctionException(ErrorCode.ROOM_DEPOSIT_REQUIRED,
                        "Phòng hẹn giờ chỉ cho phép người đã đặt cọc đủ trước giờ bắt đầu tham gia.");
            }
            synchronized (rooms) {
                requireCurrentSession(session, connectionId);
                rooms.join(auctionId, connectionId, session.getUserId(), session.getUsername(),
                        snapshot.getMaxParticipants(), snapshot.isHostedBy(session.getUserId()));
                if (snapshot.getVisibility() == RoomVisibility.PRIVATE) { sessions.grantPrivateRoomAccess(session, auctionId); }
            }
            return runtime.snapshot();
        } finally { runtime.getLock().unlock(); }
    }

    public BigDecimal placeAuctionDeposit(UserSession session, String connectionId, long auctionId, String roomPassword) {
        AuctionRuntime runtime = auctions.requireRuntime(auctionId);
        AuctionSnapshot snapshot = runtime.snapshot();
        requireRoomPassword(session, snapshot, roomPassword);
        runtime.getLock().lock();
        try {
            requireCurrentSession(session, connectionId);
            requireNotBlocked(session, auctionId);
            if (snapshot.isHostedBy(session.getUserId())) {
                throw new AuctionException(ErrorCode.AUCTION_FORBIDDEN, "Chủ phòng không phải đặt cọc.");
            }
            if (runtime.getStatusUnsafe() != AuctionStatus.OPEN || snapshot.getDepositAmount().signum() <= 0) {
                throw new AuctionException(ErrorCode.ROOM_DEPOSIT_CLOSED, "Phòng không nhận đặt cọc.");
            }
            if (!Instant.now().isBefore(runtime.getStartTime())
                    && repository.auctionDeposit(auctionId, session.getUserId()).compareTo(snapshot.getDepositAmount()) < 0) {
                throw new AuctionException(ErrorCode.ROOM_DEPOSIT_CLOSED, "Đã hết thời gian đặt cọc trước giờ bắt đầu.");
            }
            BigDecimal paid = repository.placeAuctionDeposit(auctionId, session.getUserId());
            if (snapshot.requiresPassword()) { sessions.grantPrivateRoomAccess(session, auctionId); }
            return paid;
        } finally { runtime.getLock().unlock(); }
    }

    private void requireCurrentSession(UserSession session, String connectionId) {
        if (sessions.findByConnection(connectionId)
                .filter(active -> active.getSessionToken().equals(session.getSessionToken())).isEmpty()) {
            throw new AuctionException(ErrorCode.AUTH_REQUIRED, "Phiên đăng nhập không còn hiệu lực");
        }
    }

    private void requireRoomPassword(UserSession session, AuctionSnapshot snapshot, String roomPassword) {
        if (snapshot.getVisibility() == RoomVisibility.PRIVATE
                && !snapshot.isHostedBy(session.getUserId())
                && !sessions.hasPrivateRoomAccess(session, snapshot.getAuctionId())) {
            if (roomPassword == null || roomPassword.isBlank()) {
                throw new AuctionException(ErrorCode.ROOM_PASSWORD_REQUIRED,
                        "Phòng này yêu cầu mật khẩu");
            }
            char[] password = roomPassword.toCharArray();
            if (password.length < 4 || password.length > 100) {
                throw new AuctionException(ErrorCode.ROOM_PASSWORD_INVALID, "Mật khẩu phòng phải có 4-100 ký tự");
            }
            boolean valid;
            try {
                valid = passwordHasher.verify(
                        password,
                        snapshot.getRoomPasswordSalt(),
                        snapshot.getRoomPasswordHash(),
                        snapshot.getRoomPasswordIterations());
            } finally {
                Arrays.fill(password, '\0');
            }
            if (!valid) {
                throw new AuctionException(ErrorCode.ROOM_PASSWORD_INVALID,
                        "Mật khẩu phòng không đúng");
            }
        }
    }

    public void requireReadAccess(UserSession session, long auctionId) {
        requireReadAccess(session, auctionId, false);
    }

    public void requireHistoryReadAccess(UserSession session, long auctionId) {
        requireReadAccess(session, auctionId, true);
    }

    private void requireReadAccess(UserSession session, long auctionId, boolean includeArchived) {
        AuctionRuntime runtime = includeArchived ? auctions.findRuntimeIncludingArchived(auctionId).orElseThrow(() ->
                new AuctionException(ErrorCode.AUCTION_NOT_FOUND, "Auction was not found")) : auctions.requireRuntime(auctionId);
        runtime.getLock().lock();
        try {
            requireNotBlocked(session, auctionId);
            AuctionSnapshot snapshot = runtime.snapshot();
            if (snapshot.getVisibility() == RoomVisibility.PRIVATE
                    && !snapshot.isHostedBy(session.getUserId())
                    && !sessions.hasPrivateRoomAccess(session, auctionId)) {
                throw new AuctionException(ErrorCode.ROOM_ACCESS_DENIED, "Cần quyền truy cập phòng riêng");
            }
        } finally {
            runtime.getLock().unlock();
        }
    }

    private void requireNotBlocked(UserSession session, long auctionId) {
        if (repository.isAuctionUserBlocked(auctionId, session.getUserId())) {
            throw new AuctionException(ErrorCode.USER_BLOCKED_FROM_AUCTION, "Bạn đã bị chặn khỏi phòng");
        }
    }

    private void requireUnsoldProduct(long productId) {
        if (repository.hasSoldAuctionForProduct(productId)) {
            throw new AuctionException(ErrorCode.PRODUCT_ALREADY_SOLD, "Sản phẩm đã có người thắng đấu giá");
        }
    }

    public AuctionSnapshot extendAuction(
            UserSession session,
            long auctionId,
            int extensionSeconds) {
        if (extensionSeconds < MIN_EXTENSION_SECONDS || extensionSeconds > MAX_EXTENSION_SECONDS) {
            throw new AuctionException(
                    ErrorCode.INVALID_DURATION,
                    "Thời gian gia hạn phải từ 10 đến 600 giây");
        }
        AuctionRuntime runtime = auctions.requireRuntime(auctionId);
        AuctionSnapshot snapshot;
        runtime.getLock().lock();
        try {
            requireHost(session, runtime);
            requireOpen(runtime);
            Instant oldEndTime = runtime.getEndTimeUnsafe();
            if (repository.hasBids(auctionId)) {
                throw new AuctionException(ErrorCode.AUCTION_HAS_BIDS, "Không được gia hạn thủ công sau khi có lượt ra giá");
            }
            Instant newEndTime = oldEndTime.plusSeconds(extensionSeconds);
            if (newEndTime.isAfter(runtime.getHardEndTimeUnsafe())) {
                throw new AuctionException(ErrorCode.AUCTION_EXTENSION_LIMIT, "Vượt tổng thời gian gia hạn 600 giây");
            }
            if (!repository.extendAuction(new ExtendAuctionCommit(
                    auctionId, oldEndTime, newEndTime))) {
                throw new AuctionException(
                        ErrorCode.DATABASE_CONFLICT,
                        "Trạng thái phiên đã thay đổi, hãy tải lại");
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
            if (repository.hasBids(auctionId)) {
                throw new AuctionException(ErrorCode.AUCTION_HAS_BIDS, "Không được kết thúc sớm phiên đã có lượt ra giá");
            }
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
                            "Phiên đã được kết thúc bởi yêu cầu khác"));
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
            if (runtime.getStatusUnsafe() != AuctionStatus.OPEN || !Instant.now().isBefore(runtime.getEndTimeUnsafe())) {
                throw new AuctionException(ErrorCode.AUCTION_NOT_OPEN, "Phiên đấu giá không còn mở");
            }
            if (repository.hasBids(auctionId)) {
                throw new AuctionException(
                        ErrorCode.AUCTION_HAS_BIDS,
                        "Không thể hủy phiên đã có người đặt giá");
            }
            Instant now = Instant.now();
            if (!repository.cancelAuction(new CancelAuctionCommit(auctionId, now))) {
                throw new AuctionException(
                        ErrorCode.DATABASE_CONFLICT,
                        "Không thể hủy phiên do trạng thái đã thay đổi");
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
            throw new AuctionException(ErrorCode.VALIDATION_ERROR, "Cần nhập tên tài khoản cần mời");
        }
        KickOutcome outcome;
        runtime.getLock().lock();
        try {
            requireHost(session, runtime);
            requireOpen(runtime);
            RoomMember member = rooms.findByUsername(auctionId, username)
                    .orElseThrow(() -> new AuctionException(
                            ErrorCode.USER_NOT_IN_AUCTION_ROOM,
                            "Người dùng không có trong phòng"));
            if (member.getUserId() == runtime.getHostUserId()) {
                throw new AuctionException(
                        ErrorCode.CANNOT_KICK_HOST,
                        "Không thể mời chủ trì khỏi phòng");
            }
            if (repository.hasBidsByUser(auctionId, member.getUserId())) {
                throw new AuctionException(ErrorCode.CANNOT_KICK_BIDDER, "Không được mời người đã đặt giá hợp lệ ra khỏi phòng");
            }
            repository.blockAuctionUser(new BlockAuctionUserCommit(
                    auctionId, member.getUserId(), session.getUserId(), Instant.now()));
            Set<String> removedConnections = rooms.kickUser(auctionId, member.getUserId());
            sessions.revokePrivateRoomAccess(member.getUserId(), auctionId);
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
        requireLoggedIn(session);
        if (repository.isProductDeleted(productId)) { throw new AuctionException(ErrorCode.PRODUCT_NOT_FOUND, "Sản phẩm đã được xóa"); }
        if (session == null || !session.isActive()) {
            throw new AuctionException(ErrorCode.AUTH_REQUIRED, "Cần đăng nhập để quản lý mặt hàng");
        }
        Product product = repository.findProductById(productId)
                .orElseThrow(() -> new AuctionException(
                        ErrorCode.PRODUCT_NOT_FOUND,
                        "Không tìm thấy sản phẩm"));
        if (product.getOwnerId() != session.getUserId()) {
            throw new AuctionException(
                    ErrorCode.AUCTION_FORBIDDEN,
                    "Bạn không phải chủ sở hữu sản phẩm");
        }
        return product;
    }

    private void requireHost(UserSession session, AuctionRuntime runtime) {
        if (runtime.getHostUserId() != session.getUserId()) {
            throw new AuctionException(
                    ErrorCode.AUCTION_FORBIDDEN,
                    "Chỉ chủ trì mới được thực hiện thao tác này");
        }
    }

    private void requireOpen(AuctionRuntime runtime) {
        if (runtime.getStatusUnsafe() != AuctionStatus.OPEN
                || Instant.now().isBefore(runtime.getStartTime())
                || !Instant.now().isBefore(runtime.getEndTimeUnsafe())) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_OPEN, "Phiên đấu giá không còn mở");
        }
    }

    private String cleanName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank() || name.length() > 150) {
            throw new AuctionException(
                    ErrorCode.VALIDATION_ERROR,
                    "Tên sản phẩm phải có 1-150 ký tự");
        }
        return name;
    }

    private String cleanDescription(String rawDescription) {
        String description = rawDescription == null ? "" : rawDescription.strip();
        if (description.codePointCount(0, description.length()) < 50 || description.codePointCount(0, description.length()) > 2000) {
            throw new AuctionException(
                    ErrorCode.VALIDATION_ERROR,
                    "Mô tả sản phẩm phải có 50-2000 ký tự, gồm đặc điểm, tình trạng và khuyết điểm nếu có");
        }
        return description;
    }

    private BigDecimal normalizeMoney(BigDecimal value, ErrorCode errorCode) {
        return AuctionRules.money(value, errorCode);
    }
}
