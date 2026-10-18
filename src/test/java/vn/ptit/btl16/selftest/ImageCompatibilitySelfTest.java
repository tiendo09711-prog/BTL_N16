package vn.ptit.btl16.selftest;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.media.MediaConfig;
import vn.ptit.btl16.server.media.MediaProcessor;
import vn.ptit.btl16.server.media.ProductMedia;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public final class ImageCompatibilitySelfTest {
    private ImageCompatibilitySelfTest() { }

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        MediaConfig config = MediaConfig.load(true);
        Path fixtures = config.root().getParent();
        MediaProcessor processor = new MediaProcessor(config);
        try (var input = ImageCompatibilitySelfTest.class.getResourceAsStream("/media/rainbow.heic")) {
            if (input == null) { throw new AssertionError("Missing real HEIC test resource"); }
            Files.copy(input, fixtures.resolve("phone.HEIC"));
        }
        ProductMedia heic = process(processor, fixtures.resolve("phone.HEIC"), fixtures, "phone.HEIC");
        TestSupport.equals("image/heic", heic.sourceType(), "real HEIC recognized by content");
        TestSupport.check(heic.width() >= 450 && heic.height() >= 450, "HEIC produces a full usable photo");
        try (var input = ImageCompatibilitySelfTest.class.getResourceAsStream("/media/tiled-phone.heic")) {
            Files.copy(input, fixtures.resolve("tiled.HEIC"));
        }
        ProductMedia tiled = process(processor, fixtures.resolve("tiled.HEIC"), fixtures, "tiled.HEIC");
        TestSupport.equals(4032, tiled.width(), "assemble the complete HEIC grid, not its first 512px tile");
        TestSupport.equals(3024, tiled.height(), "crop HEIC tile padding to the real photo dimensions");
        for (String extension : List.of("avif", "jp2", "ico", "ppm")) {
            Path file = fixtures.resolve("photo." + extension);
            if (extension.equals("avif")) {
                MediaSystemSelfTest.tool(config.ffmpeg(), "-f", "lavfi", "-i", "testsrc2=size=160x100:rate=1",
                        "-frames:v", "1", "-c:v", "libaom-av1", "-cpu-used", "8", "-still-picture", "1", file.toString());
            } else {
                MediaSystemSelfTest.tool(config.ffmpeg(), "-f", "lavfi", "-i", "testsrc2=size=160x100:rate=1",
                        "-frames:v", "1", file.toString());
            }
            ProductMedia image = process(processor, file, fixtures, "misleading.UPPERCASE");
            TestSupport.equals(160, image.width(), "content, not filename, selects decoder: " + extension);
        }
        Path png = fixtures.resolve("photo.png");
        Files.write(png, TestMedia.png());
        for (String name : List.of("camera.JFIF", "no-extension", "photo.HEIC")) {
            TestSupport.equals("image/png", process(processor, png, fixtures, name).sourceType(), "sniff real content: " + name);
        }
        if (config.imagePixels() >= 48_000_000) {
            Path highResolution = fixtures.resolve("phone-48MP.png");
            MediaSystemSelfTest.tool(config.ffmpeg(), "-f", "lavfi", "-i", "color=c=red:size=8000x6000:rate=1",
                    "-frames:v", "1", highResolution.toString());
            ProductMedia scaled = process(processor, highResolution, fixtures, "phone-48MP.png");
            TestSupport.equals(4096, Math.max(scaled.width(), scaled.height()), "48MP phone image scales safely before alpha compositing");
        }
        Path movie = fixtures.resolve("movie.mp4");
        MediaSystemSelfTest.tool(config.ffmpeg(), "-f", "lavfi", "-i", "testsrc2=size=160x100:rate=1",
                "-frames:v", "1", "-c:v", "libx265", "-x265-params", "log-level=error:pools=1", movie.toString());
        reject(processor, movie, fixtures, "pretend.heic");
        Path fake = fixtures.resolve("fake.png");
        Files.writeString(fake, "<svg xmlns=\"http://www.w3.org/2000/svg\"><image href=\"http://127.0.0.1/\"/></svg>");
        reject(processor, fake, fixtures, "fake.png");
        System.out.println("[PASS] ImageCompatibilitySelfTest: real HEIC/AVIF/JP2/ICO/PPM, uppercase/misnamed files, JPEG previews, video/SVG rejection");
    }

    private static ProductMedia process(MediaProcessor processor, Path input, Path root, String name) throws Exception {
        Path folder = Files.createDirectory(root.resolve(UUID.randomUUID().toString()));
        Files.copy(input, folder.resolve("source"));
        ProductMedia image = processor.process(folder, UUID.randomUUID().toString(), "IMAGE", name, 0, 0, 0, 1, 1, () -> false);
        var detail = ImageIO.read(folder.resolve("detail.jpg").toFile());
        var thumbnail = ImageIO.read(folder.resolve("thumbnail.jpg").toFile());
        TestSupport.check(detail != null && thumbnail != null && thumbnail.getWidth() <= 360 && thumbnail.getHeight() <= 360,
                "every accepted format becomes a JavaFX-compatible JPEG detail and thumbnail");
        return image;
    }

    private static void reject(MediaProcessor processor, Path input, Path root, String name) throws Exception {
        try {
            process(processor, input, root, name);
            throw new AssertionError("Non-image accepted: " + name);
        } catch (AuctionException exception) {
            TestSupport.equals(ErrorCode.MEDIA_INVALID, exception.getErrorCode(), "invalid content error");
        }
    }
}
