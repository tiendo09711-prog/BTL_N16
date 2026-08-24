package vn.ptit.btl16.server.auction.model;

import java.time.Instant;
import java.util.Objects;

public final class Product {
    private final long productId;
    private final long ownerId;
    private final String ownerUsername;
    private final String code;
    private final String name;
    private final String description;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final boolean hasImage;
    private final String imageMime;
    private final String imageName;
    private final int imageSize;
    private final long imageVersion;

    public Product(long productId, String code, String name, String description) {
        this(productId, 0L, "", code, name, description, true, null, null,
                false, "", "", 0, 0L);
    }

    public Product(
            long productId,
            long ownerId,
            String ownerUsername,
            String code,
            String name,
            String description,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        this(productId, ownerId, ownerUsername, code, name, description, active,
                createdAt, updatedAt, false, "", "", 0, 0L);
    }

    public Product(
            long productId,
            long ownerId,
            String ownerUsername,
            String code,
            String name,
            String description,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            boolean hasImage,
            String imageMime,
            String imageName,
            int imageSize,
            long imageVersion) {
        this.productId = productId;
        this.ownerId = ownerId;
        this.ownerUsername = ownerUsername == null ? "" : ownerUsername;
        this.code = Objects.requireNonNull(code, "code");
        this.name = Objects.requireNonNull(name, "name");
        this.description = description == null ? "" : description;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.hasImage = hasImage;
        this.imageMime = imageMime == null ? "" : imageMime;
        this.imageName = imageName == null ? "" : imageName;
        this.imageSize = Math.max(0, imageSize);
        this.imageVersion = Math.max(0L, imageVersion);
    }

    public long getProductId() { return productId; }
    public long getOwnerId() { return ownerId; }
    public String getOwnerUsername() { return ownerUsername; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean hasImage() { return hasImage; }
    public String getImageMime() { return imageMime; }
    public String getImageName() { return imageName; }
    public int getImageSize() { return imageSize; }
    public long getImageVersion() { return imageVersion; }
}
