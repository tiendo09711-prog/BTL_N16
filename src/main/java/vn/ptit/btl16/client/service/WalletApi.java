package vn.ptit.btl16.client.service;

import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.common.protocol.MessageType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class WalletApi {
    private final ClientTransport transport;
    public WalletApi(ClientTransport transport) { this.transport = transport; }
    public CompletableFuture<ApiResponse> get() { return transport.sendRequest(MessageType.GET_WALLET, Map.of()).thenApply(ApiResponse::new); }
    public CompletableFuture<ApiResponse> get(long auctionId) {
        return transport.sendRequest(MessageType.GET_WALLET, Map.of("auctionId", Long.toString(auctionId))).thenApply(ApiResponse::new);
    }
    public CompletableFuture<ApiResponse> submit(String id, BigDecimal amount) {
        return transport.sendRequest(MessageType.REQUEST_TOPUP, Map.of("requestId", id, "amount", amount.toPlainString())).thenApply(ApiResponse::new);
    }
    public CompletableFuture<ApiResponse> entries(Map<String, String> filters) {
        return transport.sendRequest(MessageType.GET_WALLET_ENTRIES, filters).thenApply(ApiResponse::new);
    }
    public CompletableFuture<ApiResponse> withdraw(String id, BigDecimal amount) {
        return transport.sendRequest(MessageType.REQUEST_WITHDRAWAL, Map.of("requestId", id, "amount", amount.toPlainString())).thenApply(ApiResponse::new);
    }
}
