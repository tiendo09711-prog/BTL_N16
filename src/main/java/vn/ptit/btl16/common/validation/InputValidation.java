package vn.ptit.btl16.common.validation;

import java.util.Locale;
import java.util.regex.Pattern;

public final class InputValidation {
    private static final Pattern USERNAME = Pattern.compile("[a-zA-Z0-9_]{3,30}");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("[0-9+() .-]{6,20}");

    private InputValidation() {
    }

    public static String normalizeUsername(String username) {
        return safe(username).trim().toLowerCase(Locale.ROOT);
    }

    public static void requireValidUsername(String username) {
        if (!USERNAME.matcher(safe(username)).matches()) {
            throw new ValidationException(
                    "Tên đăng nhập phải gồm 3-30 chữ cái không dấu, chữ số hoặc dấu gạch dưới");
        }
    }

    public static void requireValidPassword(char[] password) {
        int length = password == null ? 0 : password.length;
        if (length < 6 || length > 72) {
            throw new ValidationException("Mật khẩu phải có 6-72 ký tự");
        }
    }

    public static String cleanDisplayName(String displayName) {
        String value = safe(displayName).trim();
        if (value.isEmpty() || value.length() > 100) {
            throw new ValidationException("Tên hiển thị phải có 1-100 ký tự");
        }
        return value;
    }

    public static String cleanEmail(String email) {
        String value = safe(email).trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "";
        }
        if (value.length() > 120 || !EMAIL.matcher(value).matches()) {
            throw new ValidationException("Email không hợp lệ");
        }
        return value;
    }

    public static String cleanPhone(String phone) {
        String value = safe(phone).trim();
        if (value.isEmpty()) {
            return "";
        }
        if (!PHONE.matcher(value).matches()) {
            throw new ValidationException("Số điện thoại không hợp lệ");
        }
        return value;
    }

    public static String safe(String value) {
        return value == null ? "" : value;
    }
}
