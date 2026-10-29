package vn.ptit.btl16.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The seller pays the platform fee; bids and the buyer's payment remain the winning price. */
public final class AuctionFees {
    public static final BigDecimal THRESHOLD = new BigDecimal("1000000000");
    private AuctionFees() { }

    public static BigDecimal rate(BigDecimal price) {
        if (price == null || price.signum() < 0) { throw new IllegalArgumentException("Invalid sale price"); }
        return new BigDecimal(price.compareTo(THRESHOLD) <= 0 ? "0.10" : "0.15");
    }

    /** Discounts are percentage points, using the seller's rank before this sale. Zero means unranked. */
    public static BigDecimal discount(int rank) {
        return new BigDecimal(switch (rank) {
            case 1 -> "0.05";
            case 2 -> "0.04";
            case 3 -> "0.03";
            case 4, 5 -> "0.02";
            case 6, 7, 8, 9, 10 -> "0.01";
            default -> "0";
        });
    }
    public static BigDecimal rate(BigDecimal price, int rank) { return rate(price).subtract(discount(rank)); }
    public static BigDecimal fee(BigDecimal price, int rank) { return price.multiply(rate(price, rank)).setScale(2, RoundingMode.HALF_UP); }
    public static BigDecimal fee(BigDecimal price) { return price.multiply(rate(price)).setScale(2, RoundingMode.HALF_UP); }
    public static BigDecimal sellerNet(BigDecimal price) { return price.subtract(fee(price)); }
}
