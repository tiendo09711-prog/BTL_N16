package vn.ptit.btl16.common.media;

import java.util.List;

public final class ImageFormats {
    public static final String LABEL = "JPEG, PNG/APNG, BMP, GIF, TIFF, WebP, HEIC/HEIF, AVIF, JPEG 2000, ICO, PNM";
    public static final List<String> EXTENSIONS = List.of("jpg", "jpeg", "jpe", "jfif", "png", "apng", "bmp", "gif",
            "tif", "tiff", "webp", "heic", "heif", "hif", "avif", "jp2", "j2k", "jpf", "jpx", "ico", "pbm", "pgm", "ppm", "pnm");

    private ImageFormats() { }

    public static List<String> chooserPatterns() {
        return EXTENSIONS.stream().flatMap(extension -> java.util.stream.Stream.of("*." + extension,
                "*." + extension.toUpperCase(java.util.Locale.ROOT))).toList();
    }
}
