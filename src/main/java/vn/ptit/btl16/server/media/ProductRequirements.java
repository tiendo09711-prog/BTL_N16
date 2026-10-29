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
                    "Mô tả bắt buộc từ 50 đến 2000 ký tự sau khi bỏ khoảng trắng đầu/cuối. Hãy mô tả đặc điểm, tình trạng và khuyết điểm nếu có.");
        }
        long images = media.stream().filter(ProductMedia::image).count();
        long videos = media.stream().filter(value -> value.kind().equals("VIDEO")).count();
        if (images < 1 || images > 5 || videos > 1 || images + videos != media.size()
                || new HashSet<>(media.stream().map(ProductMedia::id).toList()).size() != media.size()) {
            throw new AuctionException(ErrorCode.PRODUCT_INCOMPLETE,
                    "Sản phẩm cần 1-5 ảnh và tối đa 1 video; video không thay thế ảnh. Không được lặp tệp.");
        }
    }
}
