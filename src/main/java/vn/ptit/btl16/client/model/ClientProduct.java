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
    private final boolean hasImage;
    private final String imageMime;
    private final String imageName;
    private final int imageSize;
    private final long imageVersion;

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
        this(productId, ownerId, ownerUsername, code, name, description, active,
                createdAt, updatedAt, false, "", "", 0, 0L);
    }

    public ClientProduct(
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
        this.ownerUsername = text(ownerUsername);
        this.code = text(code);
        this.name = text(name);
        this.description = text(description);
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.hasImage = hasImage;
        this.imageMime = text(imageMime);
        this.imageName = text(imageName);
        this.imageSize = imageSize;
        this.imageVersion = imageVersion;
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
    public boolean hasImage() { return hasImage; }
    public String getImageMime() { return imageMime; }
    public String getImageName() { return imageName; }
    public int getImageSize() { return imageSize; }
    public long getImageVersion() { return imageVersion; }

    @Override
    public String toString() {
        return name + " [" + code + "]" + (active ? "" : " - INACTIVE");
    }
}
