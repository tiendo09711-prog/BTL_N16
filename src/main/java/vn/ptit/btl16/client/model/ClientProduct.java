package vn.ptit.btl16.client.model;

import java.time.Instant;

public final class ClientProduct {
    private final long productId;
    private final long ownerId;
    private final String ownerUsername;
    private final String code;
    private final String name;
    private final String description;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    public ClientProduct(
            long productId,
            long ownerId,
            String ownerUsername,
            String code,
            String name,
            String description,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        this.productId = productId;
        this.ownerId = ownerId;
        this.ownerUsername = text(ownerUsername);
        this.code = text(code);
        this.name = text(name);
        this.description = text(description);
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    private String text(String value) { return value == null ? "" : value; }

    public long getProductId() { return productId; }
    public long getOwnerId() { return ownerId; }
    public String getOwnerUsername() { return ownerUsername; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return name + " [" + code + "]" + (active ? "" : " - INACTIVE");
    }
}
