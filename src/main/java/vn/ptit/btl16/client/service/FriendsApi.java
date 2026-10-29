package vn.ptit.btl16.client.service;

import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.common.protocol.MessageType;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class FriendsApi {
    private final ClientTransport transport;
    public FriendsApi(ClientTransport transport) { this.transport = transport; }
    public CompletableFuture<ApiResponse> list() { return request(MessageType.GET_FRIENDS, Map.of()); }
    public CompletableFuture<ApiResponse> lookup(String identity) {
        int separator = identity.lastIndexOf('#');
        return lookup(separator < 0 ? identity : identity.substring(0, separator), separator < 0 ? "" : identity.substring(separator + 1));
    }
    public CompletableFuture<ApiResponse> lookup(String displayName, String friendCode) {
        return request(MessageType.GET_FRIENDS, Map.of("displayName", displayName, "friendCode", friendCode));
    }
    public CompletableFuture<ApiResponse> lookup(long target) { return request(MessageType.GET_FRIENDS, target(target)); }
    public CompletableFuture<ApiResponse> add(long target) { return request(MessageType.REQUEST_FRIEND, target(target)); }
    public CompletableFuture<ApiResponse> respond(long target, String action) { return request(MessageType.RESPOND_FRIEND, Map.of("targetId", Long.toString(target), "action", action)); }
    public CompletableFuture<ApiResponse> history(long target, long before, long after, boolean read) {
        return request(MessageType.GET_PRIVATE_MESSAGES, Map.of("targetId", Long.toString(target), "beforeId", Long.toString(before), "afterId", Long.toString(after), "markRead", Boolean.toString(read)));
    }
    public CompletableFuture<ApiResponse> send(long target, String body, String nonce) { return request(MessageType.SEND_PRIVATE_MESSAGE, Map.of("targetId", Long.toString(target), "body", body, "nonce", nonce)); }
    private static Map<String, String> target(long target) { return Map.of("targetId", Long.toString(target)); }
    private CompletableFuture<ApiResponse> request(MessageType type, Map<String, String> data) { return transport.sendRequest(type, data).thenApply(ApiResponse::new); }
}
