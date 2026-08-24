package vn.ptit.btl16.server.auction.model;

import java.util.Arrays;
import java.util.Objects;

public final class ProductImage {
    private final long productId;
    private final byte[] data;
    private final String mime;
    private final String name;
    private final long version;

    public ProductImage(long productId, byte[] data, String mime, String name, long version) {
        this.productId = productId;
        this.data = Arrays.copyOf(Objects.requireNonNull(data, "data"), data.length);
        this.mime = Objects.requireNonNull(mime, "mime");
        this.name = name == null ? "" : name;
        this.version = version;
    }

    public long getProductId() { return productId; }
    public byte[] getData() { return Arrays.copyOf(data, data.length); }
    public String getMime() { return mime; }
    public String getName() { return name; }
    public int getSize() { return data.length; }
    public long getVersion() { return version; }
}
