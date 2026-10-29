package vn.ptit.btl16.common.protocol;

/** Transaction state is independent of catalog activation. Scheduled open rooms reserve a product too. */
public enum ProductTransactionStatus {
    UNTRADED("Chưa giao dịch"), AUCTIONING("Đang đấu giá"), SOLD("Đã bán"), OWNED("Đã sở hữu");

    private final String label;
    ProductTransactionStatus(String label) { this.label = label; }
    public String label() { return label; }
    public static ProductTransactionStatus parse(String value) {
        try { return valueOf(value); }
        catch (IllegalArgumentException | NullPointerException ignored) { return UNTRADED; }
    }
}
