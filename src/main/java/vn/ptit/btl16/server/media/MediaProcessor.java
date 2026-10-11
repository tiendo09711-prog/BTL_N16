package vn.ptit.btl16.server.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.server.auction.service.AuctionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class MediaProcessor {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, String> IMAGES = Map.of("png", "image/png", "mjpeg", "image/jpeg",
            "bmp", "image/bmp", "gif", "image/gif", "webp", "image/webp", "tiff", "image/tiff");
    private final MediaConfig config;

    public MediaProcessor(MediaConfig config) { this.config = config; }

    public ProductMedia process(Path folder, String id, String kind, String name, int turns,
                                double left, double top, double width, double height, BooleanSupplier cancelled) throws IOException {
        Path source = folder.resolve("source");
        if (!List.of("IMAGE", "VIDEO").contains(kind) || turns < 0 || turns > 3
                || !Double.isFinite(left + top + width + height) || left < 0 || top < 0 || width <= 0 || height <= 0
                || left + width > 1.000001 || top + height > 1.000001) {
            fail(ErrorCode.MEDIA_INVALID, "Loai tep hoac vung cat khong hop le");
        }
        JsonNode probe = probe(source, folder, cancelled);
        JsonNode stream = null;
        for (JsonNode candidate : probe.path("streams")) {
            if (candidate.path("codec_type").asText().equals("video") && stream == null) { stream = candidate; }
        }
        if (stream == null) { fail(ErrorCode.MEDIA_INVALID, "Tep khong co hinh anh/video hop le"); }
        int inputWidth = stream.path("width").asInt();
        int inputHeight = stream.path("height").asInt();
        long pixels = (long) inputWidth * inputHeight;
        boolean image = kind.equals("IMAGE");
        if (inputWidth < 2 || inputHeight < 2) { fail(ErrorCode.MEDIA_INVALID, "Khong doc duoc kich thuoc tep hop le (toi thieu 2x2)"); }
        if (pixels > (image ? config.imagePixels() : config.videoPixels())) {
            fail(ErrorCode.MEDIA_LIMIT, "Vuot nguong so diem anh: " + (image ? config.imagePixels() : config.videoPixels()));
        }
        String codec = stream.path("codec_name").asText();
        String sourceType;
        double duration = 0;
        List<String> arguments = new ArrayList<>(List.of(config.ffmpeg().toString(), "-nostdin", "-v", "error", "-xerror", "-err_detect", "crccheck+bitstream+buffer", "-y",
                "-threads", "2", "-filter_threads", "1", "-filter_complex_threads", "1",
                "-protocol_whitelist", "file,pipe", "-max_pixels", Long.toString(image ? config.imagePixels() : config.videoPixels()), "-i", source.toString()));
        if (image) {
            sourceType = IMAGES.get(codec);
            if (sourceType == null || !probe.path("format").path("format_name").asText().matches(".*(image2|_pipe|gif|webp).*")) {
                fail(ErrorCode.MEDIA_INVALID, "Anh ho tro JPEG, PNG, BMP, GIF, TIFF, WebP; SVG/HEIC/AVIF/RAW chua ho tro");
            }
            String rotate = "transpose=clock,".repeat(turns);
            String crop = "crop=iw*" + width + ":ih*" + height + ":iw*" + left + ":ih*" + top + ",";
            String filters = "[0:v]format=rgba,split[fg][bg];[bg]lutrgb=r=255:g=255:b=255:a=255[white];"
                    + "[white][fg]overlay=shortest=1," + rotate + crop + "split[large][small];[large]scale="
                    + "'min(" + config.detailEdge() + ",iw)':'min(" + config.detailEdge() + ",ih)':force_original_aspect_ratio=decrease[detail];"
                    + "[small]scale='min(360,iw)':'min(360,ih)':force_original_aspect_ratio=decrease[thumb]";
            arguments.addAll(List.of("-filter_complex", filters, "-map", "[detail]", "-frames:v", "1", "-q:v", "2",
                    "-map_metadata", "-1", folder.resolve("detail.jpg").toString(), "-map", "[thumb]", "-frames:v", "1", "-q:v", "4",
                    "-map_metadata", "-1", folder.resolve("thumbnail.jpg").toString()));
        } else {
            String format = probe.path("format").path("format_name").asText();
            if (!format.matches(".*(mov|mp4|matroska|webm|avi).*")) {
                fail(ErrorCode.MEDIA_INVALID, "Video ho tro MP4/MOV, WebM/MKV va AVI; tep khong duoc nhan dien hop le");
            }
            duration = probe.path("format").path("duration").asDouble(0);
            if (!Double.isFinite(duration) || duration <= 0 || duration > config.videoSeconds()) {
                fail(ErrorCode.MEDIA_LIMIT, "Video phai ngan hon hoac bang " + config.videoSeconds() + " giay");
            }
            sourceType = format.contains("matroska") || format.contains("webm") ? "video/webm"
                    : format.contains("avi") ? "video/x-msvideo" : "video/mp4";
            arguments.addAll(List.of("-map", "0:v:0", "-map", "0:a:0?", "-sn", "-dn", "-vf",
                    "scale=w='min(1920,iw)':h='min(1080,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2",
                    "-r", "30", "-c:v", "libx264", "-preset", "fast", "-crf", "23", "-maxrate", "4M", "-bufsize", "8M",
                    "-pix_fmt", "yuv420p", "-c:a", "aac", "-ac", "2", "-b:a", "128k", "-movflags", "+faststart",
                    "-t", Integer.toString(config.videoSeconds() + 1),
                    "-map_metadata", "-1", folder.resolve("video.mp4").toString()));
        }
        execute(arguments, folder, cancelled);
        if (!image) {
            execute(List.of(config.ffmpeg().toString(), "-nostdin", "-v", "error", "-y", "-threads", "2", "-i",
                    folder.resolve("video.mp4").toString(), "-frames:v", "1", "-vf", "scale=360:360:force_original_aspect_ratio=decrease",
                    folder.resolve("thumbnail.jpg").toString()), folder, cancelled);
        }
        Path detail = folder.resolve(image ? "detail.jpg" : "video.mp4");
        JsonNode normalized = probe(detail, folder, cancelled);
        JsonNode output = normalized.path("streams").get(0);
        if (!image && normalized.path("format").path("duration").asDouble() > config.videoSeconds() + 0.1) {
            fail(ErrorCode.MEDIA_LIMIT, "Thoi luong video thuc te vuot " + config.videoSeconds() + " giay");
        }
        long bytes;
        try (var files = Files.list(folder)) {
            bytes = files.mapToLong(path -> {
                try { return Files.size(path); } catch (IOException exception) { throw new IllegalStateException(exception); }
            }).sum();
        }
        if (bytes > config.productBytes()) { fail(ErrorCode.MEDIA_LIMIT, "Tep sau xu ly vuot ngan sach luu tru san pham"); }
        return new ProductMedia(id, 0L, kind, safeName(name), sourceType, bytes, output.path("width").asInt(),
                output.path("height").asInt(), duration, 0);
    }

    private JsonNode probe(Path source, Path folder, BooleanSupplier cancelled) throws IOException {
        byte[] output = execute(List.of(config.ffprobe().toString(), "-v", "error", "-protocol_whitelist", "file,pipe",
                "-max_pixels", Long.toString(Math.max(config.imagePixels(), config.videoPixels())), "-show_streams", "-show_format", "-of", "json", source.toString()), folder, cancelled);
        return JSON.readTree(output);
    }

    private byte[] execute(List<String> arguments, Path folder, BooleanSupplier cancelled) throws IOException {
        if (!Files.isRegularFile(Path.of(arguments.get(0)))) {
            fail(ErrorCode.MEDIA_PROCESSING_FAILED, "Chua cai bo xu ly FFmpeg/ffprobe tren may server");
        }
        Process process = new ProcessBuilder(arguments).start();
        AtomicBoolean overflow = new AtomicBoolean();
        var readers = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "media-process-output"); thread.setDaemon(true); return thread;
        });
        var output = readers.submit(() -> collect(process.getInputStream(), 262144, overflow));
        var errors = readers.submit(() -> collect(process.getErrorStream(), 65536, overflow));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(config.timeoutSeconds());
        try {
            while (!process.waitFor(50, TimeUnit.MILLISECONDS)) {
                if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                    fail(ErrorCode.UPLOAD_CANCELLED, "Da huy xu ly tep");
                }
                if (System.nanoTime() > deadline || overflow.get()) {
                    fail(ErrorCode.MEDIA_LIMIT, "Qua thoi gian/nguong xu ly tep (" + config.timeoutSeconds() + " giay)");
                }
                try (var files = Files.list(folder)) {
                    long size = files.mapToLong(path -> { try { return Files.size(path); } catch (IOException exception) { return config.productBytes() + 1; } }).sum();
                    if (size > config.productBytes()) { fail(ErrorCode.MEDIA_LIMIT, "Vuot ngan sach xu ly media san pham"); }
                }
            }
            byte[] standardOutput = output.get(5, TimeUnit.SECONDS);
            String standardError = new String(errors.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8);
            if (overflow.get()) { fail(ErrorCode.MEDIA_LIMIT, "Vuot nguong thong tin xu ly tep"); }
            if (standardError.contains("max pixel")) {
                fail(ErrorCode.MEDIA_LIMIT, "Tep vuot nguong diem anh cau hinh; can thu nho anh/video truoc khi gui");
            }
            if (process.exitValue() != 0) { fail(ErrorCode.MEDIA_INVALID, "Tep loi, khong giai ma/chuyen doi duoc"); }
            return standardOutput;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AuctionException(ErrorCode.UPLOAD_CANCELLED, "Da huy xu ly tep");
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            throw new IOException("Cannot collect media processor result", exception);
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); }
            try { process.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            output.cancel(true); errors.cancel(true); readers.shutdownNow();
            process.getInputStream().close(); process.getErrorStream().close(); process.getOutputStream().close();
        }
    }

    private static byte[] collect(InputStream input, int limit, AtomicBoolean overflow) throws IOException {
        try (input; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int read;
            while ((read = input.read(buffer)) != -1) {
                if (bytes.size() + read > limit) { overflow.set(true); return new byte[0]; }
                bytes.write(buffer, 0, read);
            }
            return bytes.toByteArray();
        }
    }

    private static String safeName(String name) {
        String clean = name == null ? "media" : name.replaceAll("[\\\\/\\p{Cntrl}]", "_");
        return clean.substring(0, Math.min(150, clean.length()));
    }

    private static void fail(ErrorCode code, String message) { throw new AuctionException(code, message); }
}
