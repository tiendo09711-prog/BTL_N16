package vn.ptit.btl16.server.media;

import java.util.List;

public record MediaProductCommit(long productId, long ownerId, String ownerUsername, String code,
                                 String name, String description, List<ProductMedia> media) {
    public MediaProductCommit {
        media = List.copyOf(media);
    }
}
