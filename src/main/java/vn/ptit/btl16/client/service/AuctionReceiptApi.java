package vn.ptit.btl16.client.service;

import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.common.protocol.MessageType;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class AuctionReceiptApi {
    private final ClientTransport transport;
    public AuctionReceiptApi(ClientTransport transport) { this.transport = transport; }
    public CompletableFuture<ApiResponse> get(long auctionId) {
        return transport.sendRequest(MessageType.GET_PERSONAL_RECEIPT, Map.of("auctionId", Long.toString(auctionId))).thenApply(ApiResponse::new);
    }
    public CompletableFuture<ApiResponse> list(String scope, String outcome, String search, long before) {
        return transport.sendRequest(MessageType.MY_AUCTION_RECEIPTS, Map.of("scope", scope, "outcome", outcome,
                "search", search, "beforeId", Long.toString(before), "limit", "30")).thenApply(ApiResponse::new);
    }
}
