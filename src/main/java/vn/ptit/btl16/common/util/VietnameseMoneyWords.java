package vn.ptit.btl16.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Reads the exact decimal amount, including fractional dong, without rounding. */
public final class VietnameseMoneyWords {
    private static final String[] DIGITS = {"không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"};

    private VietnameseMoneyWords() { }

    public static String read(BigDecimal amount) {
        if (amount == null || amount.signum() < 0 || amount.precision() > 32 || Math.abs((long) amount.scale()) > 32) {
            throw new IllegalArgumentException("Invalid spoken amount");
        }
        String[] parts = amount.setScale(2, RoundingMode.UNNECESSARY).stripTrailingZeros().toPlainString().split("\\.");
        String whole = parts[0];
        List<String> groups = new ArrayList<>();
        for (int end = whole.length(); end > 0; end -= 3) {
            groups.add(whole.substring(Math.max(0, end - 3), end));
        }
        List<String> words = new ArrayList<>();
        for (int index = groups.size() - 1; index >= 0; index--) {
            int value = Integer.parseInt(groups.get(index));
            if (value == 0) { continue; }
            words.add(hundreds(value, !words.isEmpty()));
            if (index % 3 == 1) { words.add("nghìn"); }
            if (index % 3 == 2) { words.add("triệu"); }
            for (int billion = 0; billion < index / 3; billion++) { words.add("tỷ"); }
        }
        if (words.isEmpty()) { words.add(DIGITS[0]); }
        if (parts.length == 2) {
            words.add("phẩy");
            for (char digit : parts[1].toCharArray()) { words.add(DIGITS[digit - '0']); }
        }
        words.add("đồng");
        return String.join(" ", words);
    }

    private static String hundreds(int value, boolean full) {
        List<String> words = new ArrayList<>();
        int hundreds = value / 100, tens = value / 10 % 10, ones = value % 10;
        if (hundreds > 0 || full) { words.add(DIGITS[hundreds]); words.add("trăm"); }
        if (tens > 1) { words.add(DIGITS[tens]); words.add("mươi"); }
        else if (tens == 1) { words.add("mười"); }
        else if (ones > 0 && (hundreds > 0 || full)) { words.add("linh"); }
        if (ones > 0) { words.add(ones == 1 && tens > 1 ? "mốt" : ones == 5 && tens > 0 ? "lăm" : DIGITS[ones]); }
        return String.join(" ", words);
    }
}
