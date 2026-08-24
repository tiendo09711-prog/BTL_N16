package vn.ptit.btl16.server.auction.repository;

import java.time.Instant;

public final class CreateProductCommit {
    private final long ownerId;
    private final String ownerUsername;
    private final String code;
    private final String name;
    private final String description;
    private final Instant createdAt;

    public CreateProductCommit(
            long ownerId,
            String ownerUsername,
            String code,
            String name,
            String description,
            Instant createdAt) {
        this.ownerId = ownerId;
        this.ownerUsername = ownerUsername;
        this.code = code;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }

    public long getOwnerId() { return ownerId; }
    public String getOwnerUsername() { return ownerUsername; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
