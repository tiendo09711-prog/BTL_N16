package vn.ptit.btl16.client.service;

import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.WireMessage;

public final class ApiResponse {
    private final WireMessage message;

    public ApiResponse(WireMessage message) {
        this.message = message;
    }

    public boolean isSuccess() {
        return "true".equalsIgnoreCase(message.getOrDefault("success", "false"));
    }

    public String getErrorCode() { return message.getOrDefault("errorCode", ""); }
    public String getMessageText() { return message.getOrDefault("message", ""); }
    public String get(String key) { return message.getOrDefault(key, ""); }
    public MessageType getType() { return message.getType(); }
    public WireMessage getWireMessage() { return message; }
}
