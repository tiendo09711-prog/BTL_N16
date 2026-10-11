package vn.ptit.btl16.server.auction.repository;

import vn.ptit.btl16.server.auction.model.AuctionResult;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.BidRecord;
import vn.ptit.btl16.server.auction.model.Product;
import vn.ptit.btl16.server.auction.model.ProductImage;

import java.util.List;
import java.util.Optional;

public interface AuctionRepository {
    Product saveMediaProduct(vn.ptit.btl16.server.media.MediaProductCommit commit);

    List<vn.ptit.btl16.server.media.ProductMedia> findProductMedia(long productId);

    Optional<vn.ptit.btl16.server.media.ProductMedia> findMedia(String mediaId);

    List<Long> findLegacyImageProductIds();

    void migrateLegacyImage(long productId, long expectedVersion, vn.ptit.btl16.server.media.ProductMedia media);

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
