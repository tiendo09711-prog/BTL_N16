package vn.ptit.btl16.server.media;

import java.util.List;
import java.util.Map;

public record MediaProductCommit(long productId, long ownerId, String ownerUsername, String code,
                                 String name, String description, List<ProductMedia> media, Map<String, Map<String, byte[]>> content) {
    public MediaProductCommit(long productId, long ownerId, String ownerUsername, String code,
                              String name, String description, List<ProductMedia> media) {
        this(productId, ownerId, ownerUsername, code, name, description, media, Map.of());
    }
    public MediaProductCommit {
        media = List.copyOf(media);
        content = Map.copyOf(content);
    }
}
