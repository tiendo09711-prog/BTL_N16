package vn.ptit.btl16.client.service;

import vn.ptit.btl16.client.network.NetworkClient;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.util.Money;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class AuctionApi {
    private final NetworkClient network;

    public AuctionApi(NetworkClient network) {
        this.network = network;
    }

    public CompletableFuture<ApiResponse> listAuctions() {
        return request(MessageType.AUCTION_LIST, Map.of());
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

    public CompletableFuture<ApiResponse> deactivateProduct(long productId) {
        return request(MessageType.DEACTIVATE_PRODUCT, Map.of(
                "productId", Long.toString(productId)));
    }

    public CompletableFuture<ApiResponse> createAuction(
            long productId,
            BigDecimal startPrice,
            BigDecimal minBidIncrement,
            int durationMinutes) {
        return request(MessageType.CREATE_AUCTION, Map.of(
                "productId", Long.toString(productId),
                "startPrice", Money.wire(startPrice),
                "minBidIncrement", Money.wire(minBidIncrement),
                "durationMinutes", Integer.toString(durationMinutes)));
    }

    public CompletableFuture<ApiResponse> myAuctions() {
        return request(MessageType.MY_AUCTIONS, Map.of());
    }

    public CompletableFuture<ApiResponse> join(long auctionId) {
        return request(MessageType.JOIN_AUCTION, id(auctionId));
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
