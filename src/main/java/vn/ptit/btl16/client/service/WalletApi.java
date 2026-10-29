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
    public CompletableFuture<ApiResponse> vault(String token) { return vaultRequest(MessageType.GET_VAULT, Map.of("vaultToken", token)); }
    public CompletableFuture<ApiResponse> initializeVault(String id, BigDecimal amount, String pin, String confirmation) {
        return vaultRequest(MessageType.INITIALIZE_VAULT, Map.of("requestId", id, "amount", amount.toPlainString(), "pin", pin, "pinConfirmation", confirmation));
    }
    public CompletableFuture<ApiResponse> unlockVault(String pin) { return vaultRequest(MessageType.UNLOCK_VAULT, Map.of("pin", pin)); }
    public CompletableFuture<ApiResponse> lockVault(String token) { return vaultRequest(MessageType.LOCK_VAULT, Map.of("vaultToken", token)); }
    public CompletableFuture<ApiResponse> transferVault(String id, String direction, BigDecimal amount, String token, String pin) {
        return vaultRequest(MessageType.TRANSFER_VAULT, Map.of("requestId", id, "direction", direction, "amount", amount.toPlainString(), "vaultToken", token, "pin", pin));
    }
    private CompletableFuture<ApiResponse> vaultRequest(MessageType type, Map<String, String> data) { return transport.sendRequest(type, data).thenApply(ApiResponse::new); }
}
