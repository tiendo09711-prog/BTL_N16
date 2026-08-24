package vn.ptit.btl16.server.auction.repository;

import java.time.Instant;

public final class UpdateProductCommit {
    private final long productId;
    private final long ownerId;
    private final String code;
    private final String name;
    private final String description;
    private final Instant updatedAt;

    public UpdateProductCommit(
            long productId,
            long ownerId,
            String code,
            String name,
            String description,
            Instant updatedAt) {
        this.productId = productId;
        this.ownerId = ownerId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.updatedAt = updatedAt;
    }

    public long getProductId() { return productId; }
    public long getOwnerId() { return ownerId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Instant getUpdatedAt() { return updatedAt; }
}
