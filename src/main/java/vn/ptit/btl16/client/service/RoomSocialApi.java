package vn.ptit.btl16.client.service;

import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.common.protocol.MessageType;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class RoomSocialApi {
    private final ClientTransport transport;
    public RoomSocialApi(ClientTransport transport) { this.transport = transport; }
    public CompletableFuture<ApiResponse> social(long auctionId) {
        return request(MessageType.GET_ROOM_SOCIAL, Map.of("auctionId", Long.toString(auctionId)));
    }
    public CompletableFuture<ApiResponse> social(long auctionId, long afterChatId, Collection<Long> chatIds) {
        Map<String, String> data = new LinkedHashMap<>(); data.put("auctionId", Long.toString(auctionId));
        if (afterChatId >= 0) { data.put("afterChatId", Long.toString(afterChatId)); }
        data.put("chatIds", chatIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        return request(MessageType.GET_ROOM_SOCIAL, data);
    }
    public CompletableFuture<ApiResponse> send(long auctionId, String body, String nonce) {
        return request(MessageType.SEND_ROOM_CHAT, Map.of("auctionId", Long.toString(auctionId), "body", body, "nonce", nonce));
    }
    public CompletableFuture<ApiResponse> send(long auctionId, String body, String nonce, long replyToId) {
        return request(MessageType.SEND_ROOM_CHAT, Map.of("auctionId", Long.toString(auctionId), "body", body, "nonce", nonce, "replyToId", Long.toString(replyToId)));
    }
    public CompletableFuture<ApiResponse> announcement(long auctionId, String body, String nonce) {
        return request(MessageType.SET_ROOM_ANNOUNCEMENT, Map.of("auctionId", Long.toString(auctionId), "body", body, "nonce", nonce));
    }
    public CompletableFuture<ApiResponse> reaction(long auctionId, long chatId, String emoji, boolean active, String nonce) {
        return request(MessageType.SET_ROOM_REACTION, Map.of("auctionId", Long.toString(auctionId), "chatId", Long.toString(chatId), "emoji", emoji, "active", Boolean.toString(active), "nonce", nonce));
    }
    public CompletableFuture<ApiResponse> mute(long auctionId, long participantId, int seconds, String reason, String nonce) {
        return request(MessageType.SET_ROOM_MUTE, Map.of("auctionId", Long.toString(auctionId), "participantId", Long.toString(participantId), "seconds", Integer.toString(seconds), "reason", reason, "nonce", nonce));
    }
    public CompletableFuture<ApiResponse> profile(long auctionId, long participantId) {
        return request(MessageType.GET_ROOM_PROFILE, Map.of("auctionId", Long.toString(auctionId), "participantId", Long.toString(participantId)));
    }
    public CompletableFuture<ApiResponse> ownProfile() { return request(MessageType.GET_PUBLIC_PROFILE, Map.of()); }
    public CompletableFuture<ApiResponse> saveProfile(String biography, String avatar) {
        Map<String, String> data = new LinkedHashMap<>(); data.put("biography", biography);
        if (avatar != null) { data.put("avatarBase64", avatar); }
        return request(MessageType.UPDATE_PUBLIC_PROFILE, data);
    }
    public CompletableFuture<ApiResponse> kick(long auctionId, long participantId) {
        return request(MessageType.KICK_AUCTION_USER, Map.of("auctionId", Long.toString(auctionId), "participantId", Long.toString(participantId)));
    }
    private CompletableFuture<ApiResponse> request(MessageType type, Map<String, String> data) {
        return transport.sendRequest(type, data).thenApply(ApiResponse::new);
    }
}
