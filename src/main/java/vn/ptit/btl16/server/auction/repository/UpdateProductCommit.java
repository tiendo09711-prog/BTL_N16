package vn.ptit.btl16.server.auction.repository;

import java.time.Instant;

public final class UpdateProductCommit {
    private final long productId;
    private final long ownerId;
    private final String code;
    private final String name;
    private final String description;
    private final Instant updatedAt;
    private final byte[] imageData;
    private final String imageMime;
    private final String imageName;
    private final boolean replaceImage;

    public UpdateProductCommit(
            long productId,
            long ownerId,
            String code,
            String name,
            String description,
            Instant updatedAt) {
        this(productId, ownerId, code, name, description, updatedAt, null, "", "", false);
    }

    public UpdateProductCommit(
            long productId,
            long ownerId,
            String code,
            String name,
            String description,
            Instant updatedAt,
            byte[] imageData,
            String imageMime,
            String imageName,
            boolean replaceImage) {
        this.productId = productId;
        this.ownerId = ownerId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.updatedAt = updatedAt;
        this.imageData = imageData == null ? null : imageData.clone();
        this.imageMime = imageMime == null ? "" : imageMime;
        this.imageName = imageName == null ? "" : imageName;
        this.replaceImage = replaceImage;
    }

    public long getProductId() { return productId; }
    public long getOwnerId() { return ownerId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Instant getUpdatedAt() { return updatedAt; }
    public byte[] getImageData() { return imageData == null ? null : imageData.clone(); }
    public String getImageMime() { return imageMime; }
    public String getImageName() { return imageName; }
    public boolean isReplaceImage() { return replaceImage; }
}
