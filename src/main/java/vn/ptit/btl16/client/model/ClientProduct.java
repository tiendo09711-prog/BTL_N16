package vn.ptit.btl16.client.model;

import vn.ptit.btl16.common.protocol.ProductRestorationStatus;
import vn.ptit.btl16.common.protocol.ProductTransactionStatus;

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
    private final ProductRestorationStatus restorationStatus;
    private final ProductTransactionStatus transactionStatus;

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
        this(productId, ownerId, ownerUsername, code, name, description, active,
                createdAt, updatedAt, hasImage, imageMime, imageName, imageSize, imageVersion,
                active ? ProductRestorationStatus.ACTIVE : ProductRestorationStatus.UNKNOWN);
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
            long imageVersion,
            ProductRestorationStatus restorationStatus) {
        this(productId, ownerId, ownerUsername, code, name, description, active, createdAt, updatedAt,
                hasImage, imageMime, imageName, imageSize, imageVersion, restorationStatus, ProductTransactionStatus.UNTRADED);
    }

    public ClientProduct(long productId, long ownerId, String ownerUsername, String code, String name,
                         String description, boolean active, Instant createdAt, Instant updatedAt, boolean hasImage,
                         String imageMime, String imageName, int imageSize, long imageVersion,
                         ProductRestorationStatus restorationStatus, ProductTransactionStatus transactionStatus) {
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
        this.transactionStatus = transactionStatus == null ? ProductTransactionStatus.UNTRADED : transactionStatus;
        this.restorationStatus = active ? ProductRestorationStatus.ACTIVE
                : restorationStatus == null ? ProductRestorationStatus.UNKNOWN : restorationStatus;
    }

    private String text(String value) { return value == null ? "" : value; }

    public long getProductId() { return productId; }
    public long getOwnerId() { return ownerId; }
    public String getOwnerUsername() { return ownerUsername; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
    public ProductTransactionStatus getTransactionStatus() { return transactionStatus; }
    public boolean canAuction() {
        return active && transactionStatus == ProductTransactionStatus.UNTRADED;
    }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean hasImage() { return hasImage; }
    public String getImageMime() { return imageMime; }
    public String getImageName() { return imageName; }
    public int getImageSize() { return imageSize; }
    public long getImageVersion() { return imageVersion; }
    public ProductRestorationStatus getRestorationStatus() { return restorationStatus; }
    public boolean canRestore() {
        return !active && (restorationStatus == ProductRestorationStatus.ELIGIBLE
                || restorationStatus == ProductRestorationStatus.UNKNOWN);
    }

    @Override
    public String toString() {
        return name + " [" + code + "]" + (active ? "" : " - INACTIVE");
    }
}
