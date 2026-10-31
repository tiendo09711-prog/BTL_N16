package vn.ptit.btl16.client.service;

import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.util.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class AuctionApi {
    private final ClientTransport network;

    public AuctionApi(ClientTransport network) {
        this.network = network;
    }

    public CompletableFuture<ApiResponse> listAuctions() {
        return request(MessageType.AUCTION_LIST, Map.of());
    }

    public CompletableFuture<ApiResponse> findByRoomCode(String code) {
        return request(MessageType.FIND_AUCTION_BY_CODE, Map.of("roomCode", code == null ? "" : code));
    }

    public CompletableFuture<ApiResponse> featuredAuctions() {
        return request(MessageType.GET_FEATURED_AUCTIONS, Map.of());
    }

    public CompletableFuture<ApiResponse> mediaEndpoint() {
        return request(MessageType.GET_MEDIA_ENDPOINT, Map.of());
    }

    public CompletableFuture<ApiResponse> createProduct(
            String code,
            String name,
            String description) {
        return request(MessageType.CREATE_PRODUCT, Map.of(
                "code", code == null ? "" : code,
                "name", name == null ? "" : name,
                "description", description == null ? "" : description));
    }

    public CompletableFuture<ApiResponse> createProduct(
            String code,
            String name,
            String description,
            byte[] imageData,
            String imageMime,
            String imageName) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("code", code == null ? "" : code);
        data.put("name", name == null ? "" : name);
        data.put("description", description == null ? "" : description);
        data.put("imageBase64", imageData == null ? "" : Base64.getEncoder().encodeToString(imageData));
        data.put("imageMime", imageMime == null ? "" : imageMime);
        data.put("imageName", imageName == null ? "" : imageName);
        return request(MessageType.CREATE_PRODUCT, data);
    }

    public CompletableFuture<ApiResponse> myProducts() {
        return request(MessageType.MY_PRODUCTS, Map.of());
    }

    public CompletableFuture<ApiResponse> updateProduct(
            long productId,
            String code,
            String name,
            String description) {
        return request(MessageType.UPDATE_PRODUCT, Map.of(
                "productId", Long.toString(productId),
                "code", code == null ? "" : code,
                "name", name == null ? "" : name,
                "description", description == null ? "" : description));
    }

    public CompletableFuture<ApiResponse> updateProduct(
            long productId,
            String code,
            String name,
            String description,
            byte[] imageData,
            String imageMime,
            String imageName) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("productId", Long.toString(productId));
        data.put("code", code == null ? "" : code);
        data.put("name", name == null ? "" : name);
        data.put("description", description == null ? "" : description);
        data.put("imageBase64", imageData == null ? "" : Base64.getEncoder().encodeToString(imageData));
        data.put("imageMime", imageMime == null ? "" : imageMime);
        data.put("imageName", imageName == null ? "" : imageName);
        return request(MessageType.UPDATE_PRODUCT, data);
    }

    public CompletableFuture<ApiResponse> getProductImage(long productId) {
        return request(MessageType.GET_PRODUCT_IMAGE, Map.of(
                "productId", Long.toString(productId)));
    }

    public CompletableFuture<ApiResponse> deactivateProduct(long productId) {
        return request(MessageType.DEACTIVATE_PRODUCT, Map.of(
                "productId", Long.toString(productId)));
    }

    public CompletableFuture<ApiResponse> deleteProduct(long productId) {
        return request(MessageType.DELETE_PRODUCT, Map.of("productId", Long.toString(productId)));
    }

    public CompletableFuture<ApiResponse> restoreProduct(long productId) {
        return request(MessageType.RESTORE_PRODUCT, Map.of(
                "productId", Long.toString(productId)));
    }

    public CompletableFuture<ApiResponse> createAuction(
            long productId,
            BigDecimal startPrice,
            BigDecimal minBidIncrement,
            int durationMinutes) {
        return createAuction(productId, startPrice, minBidIncrement, durationMinutes, "PUBLIC", "");
    }

    public CompletableFuture<ApiResponse> createAuction(
            long productId,
            BigDecimal startPrice,
            BigDecimal minBidIncrement,
            int durationMinutes,
            String visibility,
            String roomPassword) {
        return createAuction(productId, startPrice, minBidIncrement, durationMinutes, visibility, roomPassword, false);
    }

    public CompletableFuture<ApiResponse> createAuction(long productId, BigDecimal startPrice,
            BigDecimal minBidIncrement, int durationMinutes, String visibility, String roomPassword,
            boolean anonymousGuests) {
        return createAuction(productId, startPrice, minBidIncrement, durationMinutes, visibility,
                roomPassword, anonymousGuests, null);
    }

    public CompletableFuture<ApiResponse> createAuction(long productId, BigDecimal startPrice,
            BigDecimal minBidIncrement, int durationMinutes, String visibility, String roomPassword,
            boolean anonymousGuests, Instant startTime) {
        return createAuctionRequest(productId, startPrice, minBidIncrement, durationMinutes, visibility,
                roomPassword, anonymousGuests, startTime, 100, startTime == null ? BigDecimal.ZERO : BigDecimal.ONE, false);
    }

    public CompletableFuture<ApiResponse> createAuction(long productId, BigDecimal startPrice,
            BigDecimal minBidIncrement, int durationMinutes, String visibility, String roomPassword,
            boolean anonymousGuests, Instant startTime, int maxParticipants, BigDecimal depositAmount) {
        return createAuctionRequest(productId, startPrice, minBidIncrement, durationMinutes, visibility,
                roomPassword, anonymousGuests, startTime, maxParticipants, depositAmount, true);
    }

    private CompletableFuture<ApiResponse> createAuctionRequest(long productId, BigDecimal startPrice,
            BigDecimal minBidIncrement, int durationMinutes, String visibility, String roomPassword,
            boolean anonymousGuests, Instant startTime, int maxParticipants, BigDecimal depositAmount, boolean checkAdmission) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("productId", Long.toString(productId));
        data.put("startPrice", Money.wire(startPrice));
        data.put("minBidIncrement", Money.wire(minBidIncrement));
        data.put("durationMinutes", Integer.toString(durationMinutes));
        data.put("visibility", visibility == null ? "PUBLIC" : visibility);
        data.put("roomPassword", roomPassword == null ? "" : roomPassword);
        data.put("anonymousGuests", Boolean.toString(anonymousGuests));
        data.put("maxParticipants", Integer.toString(maxParticipants));
        data.put("depositAmount", Money.wire(depositAmount));
        if (startTime != null) { data.put("startTime", startTime.toString()); }
        if (startTime == null && !checkAdmission) { return request(MessageType.CREATE_AUCTION, data); }
        return request(MessageType.PING, Map.of()).thenCompose(server -> {
            if (!server.isSuccess()) { return CompletableFuture.completedFuture(server); }
            if ((startTime != null && !"true".equals(server.get("auctionScheduling")))
                    || (checkAdmission && !"true".equals(server.get("roomAdmission")))) {
                return CompletableFuture.completedFuture(new ApiResponse(
                        vn.ptit.btl16.common.protocol.WireMessage.response(MessageType.CREATE_AUCTION_RESULT, null, 0,
                                Map.of("success", "false", "errorCode", "VALIDATION_ERROR", "message",
                                        "Server chưa hỗ trợ đầy đủ hẹn giờ, đặt cọc và giới hạn người. Hãy cập nhật và khởi động lại Server."))));
            }
            return request(MessageType.CREATE_AUCTION, data);
        });
    }

    public CompletableFuture<ApiResponse> myAuctions() {
        return request(MessageType.MY_AUCTIONS, Map.of());
    }

    public CompletableFuture<ApiResponse> placeDeposit(long auctionId, String roomPassword) {
        return request(MessageType.PLACE_AUCTION_DEPOSIT, Map.of("auctionId", Long.toString(auctionId),
                "roomPassword", roomPassword == null ? "" : roomPassword));
    }

    public CompletableFuture<ApiResponse> join(long auctionId) {
        return request(MessageType.JOIN_AUCTION, id(auctionId));
    }

    public CompletableFuture<ApiResponse> join(long auctionId, String roomPassword) {
        return request(MessageType.JOIN_AUCTION, Map.of(
                "auctionId", Long.toString(auctionId),
                "roomPassword", roomPassword == null ? "" : roomPassword));
    }

    public CompletableFuture<ApiResponse> searchAuctions(String mode, String query) {
        return request(MessageType.SEARCH_AUCTIONS, Map.of(
                "mode", mode == null ? "ALL" : mode,
                "query", query == null ? "" : query));
    }

    public CompletableFuture<ApiResponse> leave(long auctionId) {
        return request(MessageType.LEAVE_AUCTION, id(auctionId));
    }

    public CompletableFuture<ApiResponse> bid(long auctionId, BigDecimal amount) {
        return request(MessageType.PLACE_BID, Map.of(
                "auctionId", Long.toString(auctionId),
                "amount", Money.wire(amount)));
    }

    public CompletableFuture<ApiResponse> history(long auctionId) {
        return request(MessageType.GET_BID_HISTORY, id(auctionId));
    }

    public CompletableFuture<ApiResponse> history(long auctionId, long before, Long through, boolean mine) {
        Map<String, String> data = new java.util.LinkedHashMap<>(id(auctionId));
        data.put("limit", "50"); data.put("beforeSequence", Long.toString(before));
        if (through != null) { data.put("throughSequence", Long.toString(through)); }
        data.put("mine", Boolean.toString(mine));
        return request(MessageType.GET_BID_HISTORY, data);
    }

    public CompletableFuture<ApiResponse> events(long auctionId, long before, long after) {
        return request(MessageType.GET_ROOM_EVENTS, Map.of("auctionId", Long.toString(auctionId),
                "beforeId", Long.toString(before), "afterId", Long.toString(after), "limit", "50"));
    }

    public CompletableFuture<ApiResponse> stats(long auctionId) {
        return request(MessageType.GET_AUCTION_STATS, id(auctionId));
    }

    public CompletableFuture<ApiResponse> extendAuction(long auctionId, int extensionSeconds) {
        return request(MessageType.EXTEND_AUCTION, Map.of(
                "auctionId", Long.toString(auctionId),
                "extensionSeconds", Integer.toString(extensionSeconds)));
    }

    public CompletableFuture<ApiResponse> endAuction(long auctionId) {
        return request(MessageType.END_AUCTION, id(auctionId));
    }

    public CompletableFuture<ApiResponse> cancelAuction(long auctionId) {
        return request(MessageType.CANCEL_AUCTION, id(auctionId));
    }

    public CompletableFuture<ApiResponse> kickUser(long auctionId, String username) {
        return request(MessageType.KICK_AUCTION_USER, Map.of(
                "auctionId", Long.toString(auctionId),
                "username", username == null ? "" : username));
    }

    public CompletableFuture<ApiResponse> resync(Long auctionId) {
        return request(
                MessageType.RESYNC,
                auctionId == null ? Map.of() : id(auctionId));
    }

    private Map<String, String> id(long auctionId) {
        return Map.of("auctionId", Long.toString(auctionId));
    }

    private CompletableFuture<ApiResponse> request(
            MessageType type,
            Map<String, String> data) {
        return network.sendRequest(type, data).thenApply(ApiResponse::new);
    }
}
