package vn.ptit.btl16.server.module;

import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.server.auction.controller.AuctionController;
import vn.ptit.btl16.server.routing.MessageRouter;

/** Routes shared by Phuoc, Dung, Duc and Thuan through the common network core. */
public final class AuctionModule implements ServerModule {
    private final AuctionController controller;

    public AuctionModule(AuctionController controller) {
        this.controller = controller;
    }

    @Override
    public void register(MessageRouter router) {
        router.register(MessageType.CREATE_PRODUCT, true, controller::handleCreateProduct);
        router.register(MessageType.MY_PRODUCTS, true, controller::handleMyProducts);
        router.register(MessageType.UPDATE_PRODUCT, true, controller::handleUpdateProduct);
        router.register(MessageType.DEACTIVATE_PRODUCT, true, controller::handleDeactivateProduct);
        router.register(MessageType.DELETE_PRODUCT, true, controller::handleDeleteProduct);
        router.register(MessageType.RESTORE_PRODUCT, true, controller::handleRestoreProduct);
        router.register(MessageType.GET_PRODUCT_IMAGE, true, controller::handleGetProductImage);
        router.register(MessageType.GET_MEDIA_ENDPOINT, true, controller::handleMediaEndpoint);
        router.register(MessageType.AUCTION_LIST, true, controller::handleList);
        router.register(MessageType.GET_FEATURED_AUCTIONS, true, controller::handleFeatured);
        router.register(MessageType.GET_PRODUCT_RANKING, true, controller::handleProductRanking);
        router.register(MessageType.GET_REVENUE_RANKING, true, controller::handleRevenueRanking);
        router.register(MessageType.SEARCH_AUCTIONS, true, controller::handleSearch);
        router.register(MessageType.FIND_AUCTION_BY_CODE, true, controller::handleFindByCode);
        router.register(MessageType.CREATE_AUCTION, true, controller::handleCreateAuction);
        router.register(MessageType.PLACE_AUCTION_DEPOSIT, true, controller::handlePlaceDeposit);
        router.register(MessageType.MY_AUCTIONS, true, controller::handleMyAuctions);
        router.register(MessageType.JOIN_AUCTION, true, controller::handleJoin);
        router.register(MessageType.LEAVE_AUCTION, true, controller::handleLeave);
        router.register(MessageType.GET_BID_HISTORY, true, controller::handleHistory);
        router.register(MessageType.PLACE_BID, true, controller::handlePlaceBid);
        router.register(MessageType.EXTEND_AUCTION, true, controller::handleExtendAuction);
        router.register(MessageType.END_AUCTION, true, controller::handleEndAuction);
        router.register(MessageType.CANCEL_AUCTION, true, controller::handleCancelAuction);
        router.register(MessageType.KICK_AUCTION_USER, true, controller::handleKickUser);
        router.register(MessageType.RESYNC, true, controller::handleResync);
    }
}
