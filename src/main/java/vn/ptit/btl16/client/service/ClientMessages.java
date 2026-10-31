package vn.ptit.btl16.client.service;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

public final class ClientMessages {
    private static final String FALLBACK = "Không thể thực hiện thao tác. Vui lòng thử lại.";
    private static final Pattern VIETNAMESE = Pattern.compile("[ăâđêôơưĂÂĐÊÔƠƯÀ-ÿẠ-ỹ]");
    private static final Pattern TECHNICAL = Pattern.compile("(?:[A-Za-z_$][A-Za-z0-9_$]*(?:Exception|Error)\\b|SQLSTATE|\\bat [\\w.$]+\\([^)]*:[0-9]+\\)|(?i:internal server error|connection refused|connection reset|timed out|invalid server message|\\bHTTP [45][0-9]{2}\\b))");

    private ClientMessages() { }

    public static String text(String message) {
        return error("", message);
    }

    public static String auctionStatus(String status) {
        return switch (status == null ? "" : status) {
            case "OPEN" -> "Đang đấu giá";
            case "ENDED" -> "Đã kết thúc";
            case "CANCELLED" -> "Đã hủy";
            default -> "Chưa xác định";
        };
    }

    public static String error(String code, String message) {
        String readable = message == null ? "" : message.strip()
                .replaceFirst("^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\\s*:\\s*", "")
                .replaceFirst("\\s*\\[[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+]\\s*$", "");
        if (VIETNAMESE.matcher(readable).find() && !TECHNICAL.matcher(readable).find()) {
            return readable.replaceAll("(?i)^server\\b", "Máy chủ")
                    .replaceAll("(?i)^client\\b", "Ứng dụng");
        }
        return switch (code == null ? "" : code) {
            case "AUTH_REQUIRED" -> "Vui lòng đăng nhập để thực hiện thao tác này.";
            case "SESSION_NOT_FOUND", "SESSION_EXPIRED" -> "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
            case "INVALID_CREDENTIALS" -> "Tên đăng nhập hoặc mật khẩu không đúng.";
            case "USERNAME_ALREADY_EXISTS" -> "Tên đăng nhập đã được sử dụng. Hãy chọn tên khác.";
            case "ACCOUNT_DISABLED" -> "Tài khoản đã bị khóa. Vui lòng liên hệ người quản lý.";
            case "ACCOUNT_ALREADY_ONLINE" -> "Tài khoản đang đăng nhập trên thiết bị khác.";
            case "CONNECTION_ALREADY_AUTHENTICATED" -> "Bạn đã đăng nhập. Hãy đăng xuất trước khi đổi tài khoản.";
            case "CURRENT_PASSWORD_WRONG" -> "Mật khẩu hiện tại không đúng.";
            case "PROFILE_NOT_FOUND" -> "Không tìm thấy hồ sơ người dùng.";
            case "PRODUCT_NOT_FOUND" -> "Không tìm thấy sản phẩm.";
            case "PRODUCT_CODE_EXISTS" -> "Mã sản phẩm đã được sử dụng.";
            case "PRODUCT_INACTIVE" -> "Sản phẩm đã ngừng hoạt động.";
            case "PRODUCT_IN_USE", "PRODUCT_FROZEN" -> "Sản phẩm đang có phiên đấu giá, không thể thay đổi lúc này.";
            case "PRODUCT_ALREADY_SOLD" -> "Sản phẩm đã được bán, không thể tạo phiên đấu giá mới.";
            case "PRODUCT_INCOMPLETE" -> "Hãy bổ sung đủ thông tin và ảnh sản phẩm trước khi tạo phiên đấu giá.";
            case "PRODUCT_IMAGE_TOO_LARGE", "MEDIA_LIMIT" -> "Ảnh hoặc video vượt quá giới hạn cho phép. Hãy giảm dung lượng hoặc số lượng tệp.";
            case "PRODUCT_IMAGE_UNSUPPORTED_TYPE", "PRODUCT_IMAGE_INVALID", "MEDIA_INVALID" -> "Ảnh hoặc video không hợp lệ. Hãy chọn tệp khác.";
            case "MEDIA_STORAGE_FULL" -> "Không còn đủ chỗ lưu ảnh và video. Vui lòng thử lại sau.";
            case "MEDIA_PROCESSING_FAILED" -> "Không thể xử lý ảnh hoặc video. Hãy chọn tệp khác hoặc thử lại.";
            case "UPLOAD_CANCELLED" -> "Đã hủy tải tệp.";
            case "AUCTION_NOT_FOUND" -> "Phòng đấu giá không tồn tại.";
            case "AUCTION_NOT_OPEN", "BID_AFTER_END" -> "Phiên đấu giá chưa mở hoặc đã kết thúc.";
            case "AUCTION_FORBIDDEN", "ROOM_ACCESS_DENIED" -> "Bạn không có quyền thực hiện thao tác trong phòng này.";
            case "AUCTION_ALREADY_EXISTS" -> "Sản phẩm đã có phiên đấu giá đang hoạt động.";
            case "AUCTION_HAS_BIDS" -> "Phiên đã có người đặt giá nên không thể thay đổi lúc này.";
            case "NOT_IN_AUCTION_ROOM" -> "Bạn cần vào phòng đấu giá trước khi thực hiện thao tác.";
            case "USER_NOT_IN_AUCTION_ROOM" -> "Người dùng không còn trong phòng đấu giá này.";
            case "USER_BLOCKED_FROM_AUCTION" -> "Bạn đã được mời ra và không thể tham gia lại phòng này.";
            case "ROOM_PASSWORD_REQUIRED" -> "Hãy nhập mật khẩu để tham gia phòng riêng tư.";
            case "ROOM_PASSWORD_INVALID" -> "Mật khẩu phòng không đúng. Hãy nhập lại.";
            case "ROOM_FULL" -> "Phòng đã đủ người tham gia. Vui lòng thử lại sau.";
            case "ROOM_DEPOSIT_REQUIRED" -> "Bạn cần đặt cọc đủ trước giờ bắt đầu để tham gia phòng này.";
            case "ROOM_DEPOSIT_CLOSED" -> "Đã hết thời gian đặt cọc cho phòng này.";
            case "CANNOT_KICK_HOST" -> "Không thể mời chủ phòng ra khỏi phòng.";
            case "CANNOT_KICK_BIDDER" -> "Không thể mời người đã đặt giá ra khỏi phòng.";
            case "AUCTION_EXTENSION_LIMIT" -> "Phiên đã đạt giới hạn gia hạn.";
            case "INVALID_AMOUNT" -> "Số tiền không hợp lệ. Hãy kiểm tra và nhập lại.";
            case "INSUFFICIENT_FUNDS" -> "Số dư khả dụng không đủ. Hãy nạp thêm tiền vào ví.";
            case "TOPUP_PENDING", "WITHDRAWAL_PENDING" -> "Yêu cầu đang chờ xử lý. Vui lòng kiểm tra lại sau.";
            case "TOPUP_NOT_FOUND", "WITHDRAWAL_NOT_FOUND" -> "Không tìm thấy yêu cầu giao dịch.";
            case "WALLET_LIMIT" -> "Số tiền vượt quá giới hạn của ví.";
            case "INVALID_DURATION" -> "Thời gian không hợp lệ. Hãy kiểm tra và nhập lại.";
            case "INVALID_INCREMENT" -> "Bước giá không hợp lệ. Hãy kiểm tra và nhập lại.";
            case "INVALID_SEARCH_QUERY" -> "Nội dung tìm kiếm không hợp lệ. Hãy nhập lại.";
            case "BID_TOO_LOW" -> "Giá đã thay đổi. Hãy cập nhật và kiểm tra giá tối thiểu trước khi đặt lại.";
            case "DATABASE_CONFLICT" -> "Dữ liệu vừa thay đổi. Hãy làm mới và thử lại.";
            case "BID_ALREADY_LEADING" -> "Bạn đang dẫn đầu, không thể tự nâng giá.";
            case "REQUEST_TIMEOUT" -> "Chưa nhận được phản hồi kịp thời. Hãy kiểm tra kết quả trước khi thử lại.";
            case "SERVER_UNAVAILABLE" -> "Không thể kết nối đến máy chủ. Hãy kiểm tra kết nối mạng và thử lại.";
            default -> FALLBACK;
        };
    }

    public static String message(Throwable error) {
        if (error == null) { return FALLBACK; }
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) {
            error = error.getCause();
        }
        if (error instanceof NoSuchFileException) { return "Không tìm thấy tệp đã chọn. Hãy chọn lại tệp."; }
        if (error instanceof AccessDeniedException) { return "Không thể truy cập tệp. Hãy kiểm tra quyền đọc và ghi."; }
        if (error instanceof CancellationException) { return "Đã hủy thao tác."; }
        if (error instanceof TimeoutException || error instanceof HttpTimeoutException) { return error("REQUEST_TIMEOUT", ""); }
        String readable = text(error.getMessage());
        if (!FALLBACK.equals(readable)) { return readable; }
        if (error instanceof IOException) { return error("SERVER_UNAVAILABLE", ""); }
        return FALLBACK;
    }
}
