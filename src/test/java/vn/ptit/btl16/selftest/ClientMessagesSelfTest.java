package vn.ptit.btl16.selftest;

import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.client.service.ClientMessages;
import vn.ptit.btl16.client.service.ProductMediaApi;
import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.WireMessage;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.NoSuchFileException;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

public final class ClientMessagesSelfTest {
    private ClientMessagesSelfTest() { }

    public static void main(String[] args) { run(); }

    public static void run() {
        String readable = "Bạn cần đặt cọc đủ trước giờ bắt đầu để tham gia phòng này.";
        TestSupport.equals(readable, ClientMessages.error("ROOM_DEPOSIT_REQUIRED", readable), "preserve useful Vietnamese detail");
        TestSupport.equals(readable, ClientMessages.text("ROOM_DEPOSIT_REQUIRED: " + readable), "hide a prefixed protocol code");
        TestSupport.equals(readable, ClientMessages.text(readable + " [ROOM_DEPOSIT_REQUIRED]"), "hide a media error suffix");
        TestSupport.equals(readable, ClientMessages.error("ROOM_DEPOSIT_REQUIRED", "Deposit required"), "translate English server detail");
        String named = "Không tìm thấy sản phẩm MH0001 hoặc phòng 123456 của Nguyễn Văn An.";
        TestSupport.equals(named, ClientMessages.text(named), "preserve names and useful product and room identifiers");
        String product = "Không tìm thấy sản phẩm Server [MH0001]";
        TestSupport.equals(product, ClientMessages.text(product), "product names and bracketed identifiers are not translated or hidden");
        TestSupport.equals("Máy chủ chưa phản hồi.", ClientMessages.text("Máy chủ chưa phản hồi."), "preserve sentence case");
        TestSupport.equals("Máy chủ chưa phản hồi.", ClientMessages.text("Server chưa phản hồi."), "translate technical endpoint terminology");
        for (ErrorCode code : ErrorCode.values()) {
            for (String detail : new String[]{"", "Internal server error", "Internal server error for Nguyễn Văn An", "java.lang.IllegalStateException", "Lỗi: java.sql.SQLException"}) {
                String message = ClientMessages.error(code.name(), detail);
                TestSupport.check(!message.isBlank() && !message.contains(code.name()) && !message.contains("Exception")
                        && !message.contains("Internal server error"), "no raw code or exception for " + code);
                TestSupport.check(message.chars().anyMatch(letter -> letter > 127), "Vietnamese fallback for " + code);
            }
        }
        TestSupport.equals(ClientMessages.text(null), ClientMessages.text("UNEXPECTED_FAILURE"), "unknown technical failures stay readable");
        TestSupport.equals(ClientMessages.error("REQUEST_TIMEOUT", ""), ClientMessages.message(new CompletionException(new TimeoutException())), "unwrap asynchronous timeout");
        TestSupport.equals(ClientMessages.error("SERVER_UNAVAILABLE", ""), ClientMessages.message(new ConnectException("Connection refused")), "translate network failure");
        TestSupport.check(ClientMessages.message(new NoSuchFileException("C:/private/photo.png")).contains("Không tìm thấy tệp"), "file failure has no private path");
        TestSupport.check(!ClientMessages.message(new NoSuchFileException("C:/Ảnh riêng/tài khoản.png")).contains("Ảnh riêng"), "Vietnamese paths are not mistaken for readable error messages");
        TestSupport.equals(readable, ProductMediaApi.message(new CompletionException(new IOException(readable + " [MEDIA_INVALID]"))), "media errors use the same readable copy");
        ApiResponse response = new ApiResponse(WireMessage.response(MessageType.ERROR, null, 0,
                Map.of("success", "false", "errorCode", "ROOM_DEPOSIT_REQUIRED", "message", "Deposit required")));
        TestSupport.equals(readable, response.getUserMessage(), "UI response is localized");
        TestSupport.equals("ROOM_DEPOSIT_REQUIRED", response.getErrorCode(), "protocol code remains available for control flow and diagnostics");
        TestSupport.equals("Deposit required", response.getMessageText(), "original response detail remains available for diagnostics");
        TestSupport.equals("Đang đấu giá", ClientMessages.auctionStatus("OPEN"), "legacy client status is Vietnamese");
        TestSupport.equals("Chưa xác định", ClientMessages.auctionStatus("UNKNOWN"), "unknown status does not expose an enum");
        System.out.println("[PASS] ClientMessagesSelfTest: readable Vietnamese errors, all protocol codes, media/network/file failures and preserved diagnostics");
    }
}
