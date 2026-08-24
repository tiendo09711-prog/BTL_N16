package vn.ptit.btl16.server.auction.service;

import vn.ptit.btl16.common.protocol.ErrorCode;

import java.util.Locale;

public final class ProductImageValidator {
    public static final int MAX_PRODUCT_IMAGE_BYTES = 700 * 1024;

    public ValidatedImage validate(byte[] data, String rawMime, String rawName) {
        if (data == null || data.length == 0) {
            throw new AuctionException(ErrorCode.PRODUCT_IMAGE_INVALID, "Anh san pham rong");
        }
        if (data.length > MAX_PRODUCT_IMAGE_BYTES) {
            throw new AuctionException(
                    ErrorCode.PRODUCT_IMAGE_TOO_LARGE,
                    "Anh san pham toi da 700 KB");
        }
        String mime = rawMime == null ? "" : rawMime.trim().toLowerCase(Locale.ROOT);
        boolean png = isPng(data);
        boolean jpeg = isJpeg(data);
        if (!(png || jpeg)) {
            throw new AuctionException(
                    ErrorCode.PRODUCT_IMAGE_INVALID,
                    "Du lieu anh khong phai PNG/JPEG hop le");
        }
        String detectedMime = png ? "image/png" : "image/jpeg";
        if (!mime.isBlank() && !mime.equals(detectedMime)
                && !(detectedMime.equals("image/jpeg") && mime.equals("image/jpg"))) {
            throw new AuctionException(
                    ErrorCode.PRODUCT_IMAGE_UNSUPPORTED_TYPE,
                    "MIME anh khong khop voi noi dung");
        }
        String name = rawName == null ? "" : rawName.trim();
        if (name.length() > 255) {
            name = name.substring(0, 255);
        }
        return new ValidatedImage(data.clone(), detectedMime, name);
    }

    private boolean isPng(byte[] data) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (data[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isJpeg(byte[] data) {
        return data.length >= 4
                && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8
                && (data[data.length - 2] & 0xFF) == 0xFF
                && (data[data.length - 1] & 0xFF) == 0xD9;
    }

    public static final class ValidatedImage {
        private final byte[] data;
        private final String mime;
        private final String name;

        private ValidatedImage(byte[] data, String mime, String name) {
            this.data = data;
            this.mime = mime;
            this.name = name;
        }

        public byte[] getData() { return data.clone(); }
        public String getMime() { return mime; }
        public String getName() { return name; }
    }
}
