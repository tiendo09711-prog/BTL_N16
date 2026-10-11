package vn.ptit.btl16.server.auction.service;

import vn.ptit.btl16.common.protocol.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AuctionRules {
    public static final BigDecimal MAX_MONEY = new BigDecimal("999999999999999.00");
    public static final int MIN_DURATION_MINUTES = 1;
    public static final int MAX_DURATION_MINUTES = 180;
    public static final int MIN_EXTENSION_SECONDS = 10;
    public static final int MAX_EXTENSION_SECONDS = 600;
    public static final int MAX_AUTO_EXTENSION_SECONDS = 600;

    private AuctionRules() {
    }

    public static BigDecimal money(BigDecimal value, ErrorCode errorCode) {
        if (value == null || value.precision() > 32 || Math.abs((long) value.scale()) > 32) {
            throw new AuctionException(errorCode, "Gia tri tien khong hop le");
        }
        BigDecimal normalized;
        try {
            normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new AuctionException(errorCode, "Gia tri tien chi duoc co toi da 2 so thap phan");
        }
        if (normalized.signum() <= 0 || normalized.compareTo(MAX_MONEY) > 0) {
            throw new AuctionException(errorCode, "Gia tri tien nam ngoai khoang cho phep");
        }
        return normalized;
    }

    public static void requireCompetitivePrice(BigDecimal startPrice, BigDecimal increment) {
        if (startPrice.add(increment).compareTo(MAX_MONEY) > 0) {
            throw new AuctionException(ErrorCode.INVALID_INCREMENT,
                    "Gia khoi diem cong buoc gia vuot gioi han cho phep");
        }
    }
}
