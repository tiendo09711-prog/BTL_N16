package vn.ptit.btl16.server.auction.repository;

import vn.ptit.btl16.server.auction.model.AuctionResult;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.BidRecord;
import vn.ptit.btl16.server.auction.model.Product;
import vn.ptit.btl16.server.auction.model.ProductImage;

import java.util.List;
import java.util.Optional;

public interface AuctionRepository {
    default java.util.Map<String,String> notifications(long user, long before, boolean unread, int limit) {
        return java.util.Map.of("count","0","unreadCount","0","hasMore","false","nextBeforeId","0");
    }
    default void readNotification(long user, long id, boolean read) {
        throw new vn.ptit.btl16.server.auction.service.AuctionException(vn.ptit.btl16.common.protocol.ErrorCode.AUCTION_FORBIDDEN,"No personal notification");
    }
    default java.util.List<java.util.Map<String,String>> operationEvents(Long room, String type, java.time.Instant from, java.time.Instant to, long before, int limit) { return java.util.List.of(); }
    default java.util.Map<String, String> personalReceipt(long userId, long auctionId) {
        throw new vn.ptit.btl16.server.auction.service.AuctionException(
                vn.ptit.btl16.common.protocol.ErrorCode.AUCTION_FORBIDDEN, "No confirmed personal receipt");
    }
    default java.util.Map<String, String> personalReceipts(long userId, String scope, String outcome,
                                                        String search, long before, int limit) {
        return java.util.Map.of("count", "0", "hasMore", "false", "nextBeforeId", "0");
    }
    List<vn.ptit.btl16.server.auction.model.RoomProgress.Event> findRoomEvents(long auctionId, long before, long after, int limit);
    void appendPresence(long auctionId, String type, long userId, java.time.Instant time);
    vn.ptit.btl16.server.auction.model.RoomProgress.Stats roomStats(long auctionId);
    List<vn.ptit.btl16.server.auction.model.RoomProgress.HistoryBid> bidPage(long auctionId, long before, long through, Long userId, int limit);

    Product saveMediaProduct(vn.ptit.btl16.server.media.MediaProductCommit commit);

    List<vn.ptit.btl16.server.media.ProductMedia> findProductMedia(long productId);

    Optional<vn.ptit.btl16.server.media.ProductMedia> findMedia(String mediaId);

    vn.ptit.btl16.server.media.MediaContent mediaContent(String mediaId, String variant) throws java.io.IOException;

    long mediaBytes() throws java.io.IOException;

    List<Long> findLegacyImageProductIds();

    void migrateLegacyImage(long productId, long expectedVersion, vn.ptit.btl16.server.media.ProductMedia media,
                            java.util.Map<String, byte[]> content);

    Product createProduct(CreateProductCommit commit);

    Product updateProduct(UpdateProductCommit commit);

    Optional<Product> deactivateProduct(long productId, long ownerId);

    Product restoreProduct(long productId, long ownerId);

    List<Product> findProductsByOwner(long ownerId);

    Optional<Product> findProductById(long productId);

    Optional<Product> findProductByCode(String code);

    Optional<ProductImage> findProductImage(long productId);

    boolean hasOpenAuctionForProduct(long productId);

    boolean hasAuctionsForProduct(long productId);

    boolean hasSoldAuctionForProduct(long productId);

    AuctionSnapshot createAuction(CreateAuctionCommit commit);

    List<AuctionSnapshot> findAllAuctions();

    List<AuctionSnapshot> findAuctionsByHost(long hostUserId);

    Optional<AuctionSnapshot> findAuctionById(long auctionId);

    List<BidRecord> findRecentBids(long auctionId, int limit);

    BidRecord commitAcceptedBid(BidCommit commit);

    boolean extendAuction(ExtendAuctionCommit commit);

    Optional<AuctionResult> closeAuction(CloseAuctionCommit commit);

    boolean cancelAuction(CancelAuctionCommit commit);

    boolean hasBids(long auctionId);

    boolean hasBidsByUser(long auctionId, long userId);

    void blockAuctionUser(BlockAuctionUserCommit commit);

    boolean isAuctionUserBlocked(long auctionId, long userId);

    long findMaxServerSequence();
}
