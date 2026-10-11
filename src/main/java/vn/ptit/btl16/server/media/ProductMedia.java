package vn.ptit.btl16.server.media;

public record ProductMedia(String id, long productId, String kind, String name, String sourceType,
                           long bytes, int width, int height, double duration, int position) {
    public boolean image() { return kind.equals("IMAGE"); }
    public ProductMedia attached(long ownerProduct, int order) {
        return new ProductMedia(id, ownerProduct, kind, name, sourceType, bytes, width, height, duration, order);
    }
}
