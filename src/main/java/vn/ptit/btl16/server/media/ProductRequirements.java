package vn.ptit.btl16.server.media;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.server.auction.service.AuctionException;
import java.util.HashSet;
import java.util.List;

public final class ProductRequirements {
    private ProductRequirements() { }

    public static void validate(String description, List<ProductMedia> media) {
        String text = description == null ? "" : description.strip();
        int length = text.codePointCount(0, text.length());
        if (length < 50 || length > 2000) {
            throw new AuctionException(ErrorCode.PRODUCT_INCOMPLETE,
                    "Mo ta bat buoc tu 50 den 2000 ky tu sau khi bo khoang trang dau/cuoi. Hay mo ta dac diem, tinh trang va khuyet diem neu co.");
        }
        long images = media.stream().filter(ProductMedia::image).count();
        long videos = media.stream().filter(value -> value.kind().equals("VIDEO")).count();
        if (images < 1 || images > 5 || videos > 1 || images + videos != media.size()
                || new HashSet<>(media.stream().map(ProductMedia::id).toList()).size() != media.size()) {
            throw new AuctionException(ErrorCode.PRODUCT_INCOMPLETE,
                    "San pham can 1-5 anh va toi da 1 video; video khong thay the anh. Khong duoc lap tep.");
        }
    }
}
