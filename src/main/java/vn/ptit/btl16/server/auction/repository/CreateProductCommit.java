package vn.ptit.btl16.server.auction.repository;

import java.time.Instant;

public final class CreateProductCommit {
    private final long ownerId;
    private final String ownerUsername;
    private final String code;
    private final String name;
    private final String description;
    private final Instant createdAt;
    private final byte[] imageData;
    private final String imageMime;
    private final String imageName;

    public CreateProductCommit(
            long ownerId,
            String ownerUsername,
            String code,
            String name,
            String description,
            Instant createdAt) {
        this(ownerId, ownerUsername, code, name, description, createdAt, null, "", "");
    }

    public CreateProductCommit(
            long ownerId,
            String ownerUsername,
            String code,
            String name,
            String description,
            Instant createdAt,
            byte[] imageData,
            String imageMime,
            String imageName) {
        this.ownerId = ownerId;
        this.ownerUsername = ownerUsername;
        this.code = code;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.imageData = imageData == null ? null : imageData.clone();
        this.imageMime = imageMime == null ? "" : imageMime;
        this.imageName = imageName == null ? "" : imageName;
    }

    public long getOwnerId() { return ownerId; }
    public String getOwnerUsername() { return ownerUsername; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public byte[] getImageData() { return imageData == null ? null : imageData.clone(); }
    public String getImageMime() { return imageMime; }
    public String getImageName() { return imageName; }
    public boolean hasImage() { return imageData != null && imageData.length > 0; }
}
