package vn.ptit.btl16.server.auction.controller;

import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.WireValues;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.auction.model.BidRecord;
import vn.ptit.btl16.server.auction.model.Product;
import vn.ptit.btl16.server.auction.model.ProductImage;
import vn.ptit.btl16.server.auction.model.RoomVisibility;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.auction.service.AuctionManagementService;
import vn.ptit.btl16.server.auction.service.AuctionQueryService;
import vn.ptit.btl16.server.auction.service.AuctionWireData;
import vn.ptit.btl16.server.auction.service.BidOutcome;
import vn.ptit.btl16.server.auction.service.BidService;
import vn.ptit.btl16.server.auction.service.RoomManager;
import vn.ptit.btl16.server.routing.RequestContext;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Base64;

/** Protocol adapter for auction list, room, bid, history and resync. */
public final class AuctionController {
    private final AuctionQueryService queries;
    private final BidService bids;
    private final RoomManager rooms;
    private final AuctionManagementService management;
    private vn.ptit.btl16.server.social.RoomSocialService social;
    private vn.ptit.btl16.server.auction.service.RoomProgressService progress;
    public void setProgressService(vn.ptit.btl16.server.auction.service.RoomProgressService value) { progress = value; }

    public void setSocialService(vn.ptit.btl16.server.social.RoomSocialService social) { this.social = social; }

    public AuctionController(
            AuctionQueryService queries,
            BidService bids,
            RoomManager rooms,
            AuctionManagementService management) {
        this.queries = queries;
        this.bids = bids;
        this.rooms = rooms;
        this.management = management;
    }

    public void handleCreateProduct(RequestContext context) throws Exception {
        byte[] image = decodeImage(context.value("imageBase64"));
        Product product = image == null
                ? management.createProduct(
                        context.requireSession(), context.value("code"),
                        context.value("name"), context.value("description"))
                : management.createProduct(
                        context.requireSession(), context.value("code"),
                        context.value("name"), context.value("description"), image,
                        context.value("imageMime"), context.value("imageName"));
        context.replySuccess(
                MessageType.CREATE_PRODUCT_RESULT,
                AuctionWireData.product(product, Instant.now()));
    }

    public void handleMyProducts(RequestContext context) throws Exception {
        var viewer=context.requireSession(); var hiddenSellers=management.anonymousPurchasedProducts(viewer);
        context.replySuccess(
                MessageType.MY_PRODUCTS_RESULT,
                AuctionWireData.productList(
                        management.myProducts(context.requireSession()),
                        Instant.now(), management::restorationStatus, product -> management.transactionStatus(viewer, product),
                        product -> product.getOwnerId()!=viewer.getUserId() && hiddenSellers.contains(product.getProductId())));
    }

    public void handleUpdateProduct(RequestContext context) throws Exception {
        long productId = WireValues.requireLong(context.getRequest().getData(), "productId");
        byte[] image = decodeImage(context.value("imageBase64"));
        Product product = image == null
                ? management.updateProduct(
                        context.requireSession(), productId, context.value("code"),
                        context.value("name"), context.value("description"))
                : management.updateProduct(
                        context.requireSession(), productId, context.value("code"),
                        context.value("name"), context.value("description"), image,
                        context.value("imageMime"), context.value("imageName"));
        context.replySuccess(
                MessageType.UPDATE_PRODUCT_RESULT,
                AuctionWireData.product(product, Instant.now()));
    }

    public void handleDeactivateProduct(RequestContext context) throws Exception {
        Product product = management.deactivateProduct(
                context.requireSession(),
                WireValues.requireLong(context.getRequest().getData(), "productId"));
        context.replySuccess(
                MessageType.DEACTIVATE_PRODUCT_RESULT,
                AuctionWireData.product(product, Instant.now()));
    }

    public void handleDeleteProduct(RequestContext context) throws Exception {
        long productId = WireValues.requireLong(context.getRequest().getData(), "productId");
        management.deleteProduct(context.requireSession(), productId);
        context.replySuccess(MessageType.DELETE_PRODUCT_RESULT, Map.of("productId", Long.toString(productId)));
    }

    public void handleRestoreProduct(RequestContext context) throws Exception {
        Product product = management.restoreProduct(
                context.requireSession(),
                WireValues.requireLong(context.getRequest().getData(), "productId"));
        context.replySuccess(
                MessageType.RESTORE_PRODUCT_RESULT,
                AuctionWireData.product(product, Instant.now()));
    }

    public void handleMediaEndpoint(RequestContext context) throws Exception {
        context.requireSession();
        context.replySuccess(MessageType.GET_MEDIA_ENDPOINT_RESULT, Map.of("mediaPort", Integer.toString(management.media().port())));
    }

    public void handleGetProductImage(RequestContext context) throws Exception {
        ProductImage image = management.getProductImage(
                context.requireSession(),
                WireValues.requireLong(context.getRequest().getData(), "productId"));
        context.replySuccess(
                MessageType.GET_PRODUCT_IMAGE_RESULT,
                Map.of(
                        "productId", Long.toString(image.getProductId()),
                        "imageMime", image.getMime(),
                        "imageName", image.getName(),
                        "imageSize", Integer.toString(image.getSize()),
                        "imageVersion", Long.toString(image.getVersion()),
                        "imageBase64", Base64.getEncoder().encodeToString(image.getData())));
    }

    public void handleCreateAuction(RequestContext context) throws Exception {
        String anonymous = context.value("anonymousGuests");
        if (!anonymous.isEmpty() && !anonymous.equals("true") && !anonymous.equals("false")) {
            throw new IllegalArgumentException("anonymousGuests must be true or false");
        }
        RoomVisibility visibility;
        try {
            String raw = context.value("visibility");
            visibility = raw.isBlank() ? RoomVisibility.PUBLIC : RoomVisibility.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            throw new AuctionException(
                    vn.ptit.btl16.common.protocol.ErrorCode.VALIDATION_ERROR,
                    "Loại phòng không hợp lệ");
        }
        Instant startTime;
        try { startTime = WireValues.optionalInstant(context.getRequest().getData(), "startTime"); }
        catch (java.time.DateTimeException error) {
            throw new AuctionException(vn.ptit.btl16.common.protocol.ErrorCode.VALIDATION_ERROR,
                    "Thời gian bắt đầu không hợp lệ");
        }
        int maxParticipants = WireValues.requireInt(context.getRequest().getData(), "maxParticipants");
        java.math.BigDecimal depositAmount = startTime != null || !context.value("depositAmount").isBlank()
                ? WireValues.requireDecimal(context.getRequest().getData(), "depositAmount") : java.math.BigDecimal.ZERO;
        AuctionSnapshot snapshot = management.createAuction(
                context.requireSession(),
                WireValues.requireLong(context.getRequest().getData(), "productId"),
                WireValues.requireDecimal(context.getRequest().getData(), "startPrice"),
                WireValues.requireDecimal(context.getRequest().getData(), "minBidIncrement"),
                WireValues.requireInt(context.getRequest().getData(), "durationMinutes"),
                visibility,
                context.value("roomPassword"), Boolean.parseBoolean(context.value("anonymousGuests")),
                startTime, maxParticipants, depositAmount);
        context.replySuccess(
                MessageType.CREATE_AUCTION_RESULT,
                AuctionWireData.snapshot(snapshot, List.of(), 0, Instant.now()));
    }

    public void handleMyAuctions(RequestContext context) throws Exception {
        Instant snapshotAt = Instant.now();
        context.replySuccess(
                MessageType.MY_AUCTIONS_RESULT,
                AuctionWireData.auctionList(
                        management.myAuctions(context.requireSession()),
                        rooms,
                        snapshotAt));
    }

    public void handlePlaceDeposit(RequestContext context) throws Exception {
        long auctionId = auctionId(context);
        var paid = management.placeAuctionDeposit(context.requireSession(), context.getConnection().getConnectionId(),
                auctionId, context.value("roomPassword"));
        context.replySuccess(MessageType.PLACE_AUCTION_DEPOSIT_RESULT, Map.of(
                "auctionId", Long.toString(auctionId), "paidDeposit", paid.toPlainString(),
                "message", "Đã đặt cọc đủ. Bạn có thể vào phòng khi bắt đầu và còn chỗ trống.",
                "serverNow", Instant.now().toString()));
    }

    public void handleList(RequestContext context) throws Exception {
        Instant snapshotAt = Instant.now();
        context.replySuccess(
                MessageType.AUCTION_LIST_RESULT,
                AuctionWireData.auctionList(queries.listAuctions(), rooms, snapshotAt));
    }

    public void handleFeatured(RequestContext context) throws Exception {
        context.replySuccess(MessageType.FEATURED_AUCTIONS_RESULT,
                queries.featured(context.requireSession().getUserId(), rooms, Instant.now()));
    }

    public void handleSearch(RequestContext context) throws Exception {
        Instant snapshotAt = Instant.now();
        context.replySuccess(
                MessageType.SEARCH_AUCTIONS_RESULT,
                AuctionWireData.auctionList(
                        queries.searchAuctions(context.value("mode"), context.value("query")),
                        rooms,
                        snapshotAt));
    }

    public void handleProductRanking(RequestContext context) throws Exception {
        context.requireSession();
        context.replySuccess(MessageType.PRODUCT_RANKING_RESULT, queries.topProducts());
    }
    public void handleRevenueRanking(RequestContext context) throws Exception {
        context.requireSession();
        vn.ptit.btl16.common.util.RevenuePeriod period;
        try { period = vn.ptit.btl16.common.util.RevenuePeriod.parse(context.value("period")); }
        catch (IllegalArgumentException invalid) {
            throw new AuctionException(vn.ptit.btl16.common.protocol.ErrorCode.VALIDATION_ERROR, "Invalid revenue period");
        }
        context.replySuccess(MessageType.REVENUE_RANKING_RESULT, queries.topSellers(period));
    }

    public void handleJoin(RequestContext context) throws Exception {
        long auctionId = auctionId(context);
        AuctionSnapshot snapshot = management.joinAuction(
                context.requireSession(),
                context.getConnection().getConnectionId(),
                auctionId,
                context.value("roomPassword"));
        List<BidRecord> history = queries.getRecentBids(auctionId);
        context.replySuccess(
                MessageType.AUCTION_SNAPSHOT,
                AuctionWireData.snapshot(
                        snapshot,
                        history,
                        rooms,
                        Instant.now()));
    }

    public void handleFindByCode(RequestContext context) throws Exception {
        context.replySuccess(MessageType.AUCTION_CODE_RESULT,
                AuctionWireData.snapshot(queries.findByRoomCode(context.value("roomCode")), List.of(), rooms, Instant.now()));
    }

    public void handleLeave(RequestContext context) throws Exception {
        long auctionId = auctionId(context);
        rooms.leave(auctionId, context.getConnection().getConnectionId());
        context.replySuccess(
                MessageType.LEAVE_AUCTION_RESULT,
                Map.of(
                        "auctionId", Long.toString(auctionId),
                        "message", "Đã rời phòng đấu giá"));
    }

    public void handleHistory(RequestContext context) throws Exception {
        if (progress != null) { progress.history(context); return; }
        long auctionId = auctionId(context);
        management.requireReadAccess(context.requireSession(), auctionId);
        Map<String, String> data = AuctionWireData.history(queries.getRecentBids(auctionId), Instant.now());
        data.put("auctionId", Long.toString(auctionId));
        context.replySuccess(MessageType.BID_HISTORY_RESULT, data);
    }

    public void handlePlaceBid(RequestContext context) throws Exception {
        try {
            long auctionId = auctionId(context);
            BidOutcome outcome = bids.placeBid(
                    context.requireSession(),
                    context.getConnection().getConnectionId(),
                    auctionId,
                    WireValues.requireDecimal(context.getRequest().getData(), "amount"));
            Map<String, String> data = AuctionWireData.bidUpdate(
                    outcome.getAuction(),
                    outcome.getBid(),
                    outcome.getPreviousWinnerId(),
                    outcome.getPreviousWinnerUsername(),
                    outcome.isExtended(),
                    Instant.now(),
                    rooms.participantCount(auctionId));
            data.put("message", "Lượt ra giá hợp lệ");
            context.replySuccess(MessageType.BID_ACCEPTED, data);
        } catch (AuctionException exception) {
            replyBidRejected(context, exception);
        } catch (IllegalArgumentException exception) {
            context.replyFailure(
                    MessageType.BID_REJECTED,
                    vn.ptit.btl16.common.protocol.ErrorCode.INVALID_AMOUNT,
                    exception.getMessage());
        }
    }

    public void handleExtendAuction(RequestContext context) throws Exception {
        long auctionId = auctionId(context);
        AuctionSnapshot snapshot = management.extendAuction(
                context.requireSession(),
                auctionId,
                WireValues.requireInt(context.getRequest().getData(), "extensionSeconds"));
        context.replySuccess(
                MessageType.EXTEND_AUCTION_RESULT,
                AuctionWireData.snapshot(
                        snapshot,
                        queries.getRecentBids(auctionId),
                        rooms,
                        Instant.now()));
    }

    public void handleEndAuction(RequestContext context) throws Exception {
        long auctionId = auctionId(context);
        var result = management.endAuction(context.requireSession(), auctionId);
        AuctionSnapshot snapshot = queries.getSnapshot(auctionId);
        context.replySuccess(
                MessageType.END_AUCTION_RESULT,
                AuctionWireData.ended(snapshot, result, Instant.now()));
    }

    public void handleCancelAuction(RequestContext context) throws Exception {
        AuctionSnapshot snapshot = management.cancelAuction(
                context.requireSession(), auctionId(context));
        context.replySuccess(
                MessageType.CANCEL_AUCTION_RESULT,
                AuctionWireData.cancelled(snapshot, Instant.now()));
    }

    public void handleKickUser(RequestContext context) throws Exception {
        long auctionId = auctionId(context);
        var outcome = management.kickUser(
                context.requireSession(),
                auctionId,
                social == null ? context.value("username") : social.kickUsername(context.requireSession(),
                        auctionId(context), context.value("username"), context.value("participantId")));
        context.replySuccess(
                MessageType.KICK_AUCTION_USER_RESULT,
                Map.of(
                        "auctionId", Long.toString(auctionId),
                        "userId", Long.toString(outcome.getUserId()),
                        "username", outcome.getUsername(),
                        "message", "Đã mời người dùng khỏi phòng"));
    }

    public void handleResync(RequestContext context) throws Exception {
        String rawAuctionId = context.value("auctionId");
        if (rawAuctionId.isBlank()) {
            Instant snapshotAt = Instant.now();
            context.replySuccess(
                    MessageType.RESYNC_RESULT,
                    AuctionWireData.auctionList(queries.listAuctions(), rooms, snapshotAt));
            return;
        }
        long auctionId = Long.parseLong(rawAuctionId);
        management.requireReadAccess(context.requireSession(), auctionId);
        AuctionSnapshot current = queries.getSnapshot(auctionId);
        AuctionSnapshot snapshot = current.isOpenAt(Instant.now())
                ? management.joinAuction(
                        context.requireSession(),
                        context.getConnection().getConnectionId(),
                        auctionId)
                : current;
        context.replySuccess(
                MessageType.RESYNC_RESULT,
                AuctionWireData.snapshot(
                        snapshot,
                        queries.getRecentBids(auctionId),
                        rooms,
                        Instant.now()));
    }

    private long auctionId(RequestContext context) {
        return WireValues.requireLong(context.getRequest().getData(), "auctionId");
    }

    private void replyBidRejected(RequestContext context, AuctionException exception)
            throws IOException {
        context.replyFailure(
                MessageType.BID_REJECTED,
                exception.getErrorCode(),
                exception.getMessage());
    }

    private byte[] decodeImage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new AuctionException(
                    vn.ptit.btl16.common.protocol.ErrorCode.PRODUCT_IMAGE_INVALID,
                    "Dữ liệu Base64 của ảnh không hợp lệ");
        }
    }
}
