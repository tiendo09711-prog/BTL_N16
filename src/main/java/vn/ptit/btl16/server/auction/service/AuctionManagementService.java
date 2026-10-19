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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.Arrays;

public final class AuctionManagementService {
    private static final Pattern PRODUCT_CODE = Pattern.compile("[A-Z0-9_-]{2,30}");
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
        return repository.findProductsByOwner(session.getUserId());
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
        String clean = cleanCode(code);
        Optional<Product> duplicate = repository.findProductByCode(clean);
        if (duplicate.isPresent() && duplicate.get().getProductId() != productId) {
            throw new AuctionException(ErrorCode.PRODUCT_CODE_EXISTS, "Ma san pham da ton tai");
        }
        ProductRequirements.validate(description, selected);
        return new MediaProductCommit(productId, session.getUserId(), session.getUsername(),
                clean, cleanName(name), cleanDescription(description), selected);
    }

    public Product saveMediaProduct(UserSession session, long productId, String code, String name,
                                    String description, List<ProductMedia> selected, java.util.Map<String, java.nio.file.Path> folders) {
        MediaProductCommit prepared = prepareMediaProduct(session, productId, code, name, description, selected);
        return repository.saveMediaProduct(new MediaProductCommit(prepared.productId(), prepared.ownerId(), prepared.ownerUsername(),
                prepared.code(), prepared.name(), prepared.description(), prepared.media(), folders));
    }

    public void requireMediaEdit(UserSession session, long productId) {
        requireLoggedIn(session);
        if (productId == 0) { return; }
        requireOwnedProduct(session, productId);
        if (repository.hasAuctionsForProduct(productId)) {
            throw new AuctionException(ErrorCode.PRODUCT_FROZEN, "San pham da tung dau gia: noi dung, thu tu, anh dai dien va media da dong bang");
        }
    }

    private void requireLoggedIn(UserSession session) {
        if (session == null || !session.isActive()) { throw new AuctionException(ErrorCode.AUTH_REQUIRED, "Can dang nhap"); }
    }

    public void requireMediaRead(UserSession session, long productId, long auctionId) {
        requireLoggedIn(session);
        Product product = repository.findProductById(productId).orElseThrow(() -> new AuctionException(
                ErrorCode.PRODUCT_NOT_FOUND, "Khong tim thay san pham"));
        boolean allowed = auctionId == 0 && product.getOwnerId() == session.getUserId();
        if (!allowed) {
            allowed = (auctionId == 0 ? repository.findAllAuctions().stream() : repository.findAuctionById(auctionId).stream()).anyMatch(auction ->
                    auction.getProduct().getProductId() == productId && (auctionId == 0 || auction.getAuctionId() == auctionId)
                    && !repository.isAuctionUserBlocked(auction.getAuctionId(), session.getUserId())
                    && (auction.getVisibility() == RoomVisibility.PUBLIC || auction.isHostedBy(session.getUserId())
                        || sessions.hasPrivateRoomAccess(session, auction.getAuctionId())));
        }
        if (!allowed) { throw new AuctionException(ErrorCode.ROOM_ACCESS_DENIED, "Khong co quyen xem media trong phong nay"); }
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
                    "Khong the vo hieu hoa san pham dang co phien dau gia mo");
        }
        return repository.deactivateProduct(productId, session.getUserId())
                .orElseThrow(() -> new AuctionException(
                        ErrorCode.PRODUCT_NOT_FOUND,
                        "Khong tim thay san pham"));
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
                    "Khong the khoi phuc mat hang dang co phien dau gia mo");
        }
        if (status == ProductRestorationStatus.SOLD) {
            throw new AuctionException(ErrorCode.PRODUCT_ALREADY_SOLD,
                    "Mat hang da co nguoi thang, khong duoc khoi phuc de dau gia lai ke ca chua thanh toan");
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
        Product product = requireOwnedProduct(session, productId);
        if (!product.isActive()) {
            throw new AuctionException(ErrorCode.PRODUCT_INACTIVE, "San pham da bi vo hieu hoa");
        }
        if (repository.hasOpenAuctionForProduct(productId)) {
            throw new AuctionException(ErrorCode.AUCTION_ALREADY_EXISTS,
                    "San pham da co phien dau gia dang mo");
        }
        if (durationMinutes < MIN_DURATION_MINUTES || durationMinutes > MAX_DURATION_MINUTES) {
            throw new AuctionException(ErrorCode.INVALID_DURATION,
                    "Thoi luong phai tu 1 den 180 phut");
        }
        RoomVisibility safeVisibility = visibility == null ? RoomVisibility.PUBLIC : visibility;
        PasswordHash passwordHash = null;
        char[] password = rawRoomPassword == null ? new char[0] : rawRoomPassword.toCharArray();
        try {
            if (safeVisibility == RoomVisibility.PRIVATE) {
                if (password.length < 4 || password.length > 100) {
                    throw new AuctionException(ErrorCode.ROOM_PASSWORD_REQUIRED,
                            "Mat khau phong rieng phai co 4-100 ky tu");
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
        Instant startTime = Instant.now();
        AuctionSnapshot snapshot = repository.createAuction(new CreateAuctionCommit(
                session.getUserId(), session.getUsername(), productId, startPrice, minIncrement,
                startTime, startTime.plusSeconds(durationMinutes * 60L), safeVisibility,
                passwordHash == null ? "" : passwordHash.getHashBase64(),
                passwordHash == null ? "" : passwordHash.getSaltBase64(),
                passwordHash == null ? null : passwordHash.getIterations()));
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
                    "Ban da bi chu tri moi khoi phong nay");
        }
        if (!snapshot.isOpenAt(Instant.now())) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_OPEN, "Phien dau gia khong con mo");
        }
        if (snapshot.getVisibility() == RoomVisibility.PRIVATE
                && !snapshot.isHostedBy(session.getUserId())
                && !sessions.hasPrivateRoomAccess(session, auctionId)) {
            if (roomPassword == null || roomPassword.isBlank()) {
                throw new AuctionException(ErrorCode.ROOM_PASSWORD_REQUIRED,
                        "Phong nay yeu cau mat khau");
            }
            char[] password = roomPassword.toCharArray();
            if (password.length < 4 || password.length > 100) {
                throw new AuctionException(ErrorCode.ROOM_PASSWORD_INVALID, "Mat khau phong phai co 4-100 ky tu");
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
                        "Mat khau phong khong dung");
            }
        }
        runtime.getLock().lock();
        try {
            requireOpen(runtime);
            requireNotBlocked(session, auctionId);
            if (sessions.findByConnection(connectionId)
                    .filter(active -> active.getSessionToken().equals(session.getSessionToken())).isEmpty()) {
                throw new AuctionException(ErrorCode.AUTH_REQUIRED, "Phien dang nhap khong con hieu luc");
            }
            if (snapshot.getVisibility() == RoomVisibility.PRIVATE) {
                sessions.grantPrivateRoomAccess(session, auctionId);
            }
            rooms.join(auctionId, connectionId, session.getUserId(), session.getUsername());
            return runtime.snapshot();
        } finally {
            runtime.getLock().unlock();
        }
    }

    public void requireReadAccess(UserSession session, long auctionId) {
        AuctionRuntime runtime = auctions.requireRuntime(auctionId);
        runtime.getLock().lock();
        try {
            requireNotBlocked(session, auctionId);
            AuctionSnapshot snapshot = runtime.snapshot();
            if (snapshot.getVisibility() == RoomVisibility.PRIVATE
                    && !snapshot.isHostedBy(session.getUserId())
                    && !sessions.hasPrivateRoomAccess(session, auctionId)) {
                throw new AuctionException(ErrorCode.ROOM_ACCESS_DENIED, "Can quyen truy cap phong rieng");
            }
        } finally {
            runtime.getLock().unlock();
        }
    }

    private void requireNotBlocked(UserSession session, long auctionId) {
        if (repository.isAuctionUserBlocked(auctionId, session.getUserId())) {
            throw new AuctionException(ErrorCode.USER_BLOCKED_FROM_AUCTION, "Ban da bi chan khoi phong");
        }
    }

    private void requireUnsoldProduct(long productId) {
        if (repository.hasSoldAuctionForProduct(productId)) {
            throw new AuctionException(ErrorCode.PRODUCT_ALREADY_SOLD, "San pham da co nguoi thang dau gia");
        }
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
            if (repository.hasBids(auctionId)) {
                throw new AuctionException(ErrorCode.AUCTION_HAS_BIDS, "Khong duoc gia han thu cong sau khi co bid");
            }
            Instant newEndTime = oldEndTime.plusSeconds(extensionSeconds);
            if (newEndTime.isAfter(runtime.getHardEndTimeUnsafe())) {
                throw new AuctionException(ErrorCode.AUCTION_EXTENSION_LIMIT, "Vuot tong ngan sach gia han 600 giay");
            }
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
            if (repository.hasBids(auctionId)) {
                throw new AuctionException(ErrorCode.AUCTION_HAS_BIDS, "Khong duoc ket thuc som phien da co bid");
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
            if (repository.hasBidsByUser(auctionId, member.getUserId())) {
                throw new AuctionException(ErrorCode.CANNOT_KICK_BIDDER, "Khong duoc kick nguoi da dat gia hop le");
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
        if (session == null || !session.isActive()) {
            throw new AuctionException(ErrorCode.AUTH_REQUIRED, "Can dang nhap de quan ly mat hang");
        }
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
                || Instant.now().isBefore(runtime.getStartTime())
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
        String description = rawDescription == null ? "" : rawDescription.strip();
        if (description.codePointCount(0, description.length()) < 50 || description.codePointCount(0, description.length()) > 2000) {
            throw new AuctionException(
                    ErrorCode.VALIDATION_ERROR,
                    "Mo ta san pham phai co 50-2000 ky tu, gom dac diem, tinh trang va khuyet diem neu co");
        }
        return description;
    }

    private BigDecimal normalizeMoney(BigDecimal value, ErrorCode errorCode) {
        return AuctionRules.money(value, errorCode);
    }
}
