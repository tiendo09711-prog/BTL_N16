package vn.ptit.btl16.client.service;

import vn.ptit.btl16.client.network.NetworkClient;
import vn.ptit.btl16.common.protocol.MessageType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class AccountApi {
    private final NetworkClient network;

    public AccountApi(NetworkClient network) {
        this.network = network;
    }

    public CompletableFuture<ApiResponse> register(
            String username,
            String password,
            String displayName,
            String email,
            String phone) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("username", username);
        data.put("password", password);
        data.put("displayName", displayName);
        data.put("email", email);
        data.put("phone", phone);
        return request(MessageType.REGISTER, data);
    }

    public CompletableFuture<ApiResponse> login(String username, String password) {
        return request(MessageType.LOGIN, Map.of(
                "username", username,
                "password", password));
    }

    public CompletableFuture<ApiResponse> resumeSession(String sessionToken) {
        return request(MessageType.RESUME_SESSION, Map.of("sessionToken", sessionToken));
    }

    public CompletableFuture<ApiResponse> logout() {
        return request(MessageType.LOGOUT, Map.of());
    }

    public CompletableFuture<ApiResponse> getProfile() {
        return request(MessageType.GET_PROFILE, Map.of());
    }

    public CompletableFuture<ApiResponse> updateProfile(
            String displayName,
            String email,
            String phone) {
        return request(MessageType.UPDATE_PROFILE, Map.of(
                "displayName", displayName,
                "email", email,
                "phone", phone));
    }

    public CompletableFuture<ApiResponse> changePassword(
            String currentPassword,
            String newPassword) {
        return request(MessageType.CHANGE_PASSWORD, Map.of(
                "currentPassword", currentPassword,
                "newPassword", newPassword));
    }

    public CompletableFuture<ApiResponse> ping() {
        return network.ping().thenApply(ApiResponse::new);
    }

    private CompletableFuture<ApiResponse> request(
            MessageType type,
            Map<String, String> data) {
        return network.sendRequest(type, data).thenApply(ApiResponse::new);
    }
}
