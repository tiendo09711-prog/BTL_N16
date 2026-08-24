package vn.ptit.btl16.server.auction.repository;

import vn.ptit.btl16.server.auction.model.AuctionResult;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.BidRecord;
import vn.ptit.btl16.server.auction.model.Product;
import vn.ptit.btl16.server.auction.model.ProductImage;

import java.util.List;
import java.util.Optional;

public interface AuctionRepository {
    Product createProduct(CreateProductCommit commit);

    Product updateProduct(UpdateProductCommit commit);

    Optional<Product> deactivateProduct(long productId, long ownerId);

    List<Product> findProductsByOwner(long ownerId);

    Optional<Product> findProductById(long productId);

    Optional<Product> findProductByCode(String code);

    Optional<ProductImage> findProductImage(long productId);

    boolean hasOpenAuctionForProduct(long productId);

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

    void blockAuctionUser(BlockAuctionUserCommit commit);

    boolean isAuctionUserBlocked(long auctionId, long userId);

    long findMaxServerSequence();
}
