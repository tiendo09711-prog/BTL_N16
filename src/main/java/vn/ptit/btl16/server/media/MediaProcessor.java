package vn.ptit.btl16.server.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.common.media.ImageFormats;
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
    private static final Map<String, String> IMAGES = Map.ofEntries(
            Map.entry("png", "image/png"), Map.entry("apng", "image/apng"), Map.entry("mjpeg", "image/jpeg"),
            Map.entry("bmp", "image/bmp"), Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"),
            Map.entry("tiff", "image/tiff"), Map.entry("jpeg2000", "image/jp2"),
            Map.entry("pbm", "image/x-portable-bitmap"), Map.entry("pgm", "image/x-portable-graymap"),
            Map.entry("ppm", "image/x-portable-pixmap"), Map.entry("pam", "image/x-portable-arbitrarymap"));
    private final MediaConfig config;

    public MediaProcessor(MediaConfig config) { this.config = config; }

    public Processed process(byte[] source, String id, String kind, String name, int turns,
                                double left, double top, double width, double height, BooleanSupplier cancelled) throws IOException {
        try (MediaInputServer input = new MediaInputServer()) {
            return process(input, source, id, kind, name, turns, left, top, width, height, cancelled);
        }
    }

    private Processed process(MediaInputServer input, byte[] source, String id, String kind, String name, int turns,
                              double left, double top, double width, double height, BooleanSupplier cancelled) throws IOException {
        String sourceUrl = input.add(source);
        Map<String, byte[]> content = new java.util.LinkedHashMap<>();
        content.put("source", source);
        if (!List.of("IMAGE", "VIDEO").contains(kind) || turns < 0 || turns > 3
                || !Double.isFinite(left + top + width + height) || left < 0 || top < 0 || width <= 0 || height <= 0
                || left + width > 1.000001 || top + height > 1.000001) {
            fail(ErrorCode.MEDIA_INVALID, "Loai tep hoac vung cat khong hop le");
        }
        JsonNode probe = probe(sourceUrl, cancelled);
        JsonNode stream = null;
        for (JsonNode candidate : probe.path("streams")) {
            if (candidate.path("codec_type").asText().equals("video") && stream == null) { stream = candidate; }
        }
        if (stream == null) { fail(ErrorCode.MEDIA_INVALID, "Tep khong co hinh anh/video hop le"); }
        int inputWidth = stream.path("width").asInt();
        int inputHeight = stream.path("height").asInt();
        JsonNode grid = null;
        if (kind.equals("IMAGE")) {
            for (JsonNode group : probe.path("stream_groups")) {
                if (group.path("type").asText().equals("Tile Grid") && (grid == null || group.path("disposition").path("default").asInt() == 1)) {
                    grid = group.path("components").path(0);
                    inputWidth = grid.path("coded_width").asInt(); inputHeight = grid.path("coded_height").asInt();
                }
            }
        }
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
                "-protocol_whitelist", "http,tcp,pipe", "-format_whitelist", INPUT_FORMATS,
                "-max_pixels", Long.toString(image ? config.imagePixels() : config.videoPixels()), "-i", sourceUrl));
        if (image) {
            String format = probe.path("format").path("format_name").asText();
            sourceType = IMAGES.get(codec);
            if (format.matches(".*(mov|mp4).*") && imageBrand(probe, "heic", "heix", "hevc", "hevx", "mif1") && codec.equals("hevc")) {
                sourceType = "image/heic";
            } else if (format.matches(".*(mov|mp4).*") && imageBrand(probe, "avif", "avis") && codec.equals("av1")) {
                sourceType = "image/avif";
            } else if (sourceType == null || !format.matches(".*(image2|_pipe|gif|webp|apng|ico).*")) {
                fail(ErrorCode.MEDIA_INVALID, "Anh ho tro " + ImageFormats.LABEL + "; tep loi, SVG/RAW hoac codec khac can chuyen doi truoc");
            }
            if (format.equals("ico")) { sourceType = "image/x-icon"; }
            String rotate = "transpose=clock,".repeat(turns);
            String crop = "crop=iw*" + width + ":ih*" + height + ":iw*" + left + ":ih*" + top + ",";
            String filters = imageInput(probe, grid) + rotate + crop + "scale="
                    + "'min(" + config.detailEdge() + ",iw)':'min(" + config.detailEdge() + ",ih)':force_original_aspect_ratio=decrease,"
                    + "format=rgba,split[fg][bg];[bg]lutrgb=r=255:g=255:b=255:a=255[white];"
                    + "[white][fg]overlay=shortest=1[detail]";
            arguments.addAll(List.of("-filter_complex", filters, "-map", "[detail]", "-frames:v", "1", "-q:v", "2",
                    "-map_metadata", "-1", "-c:v", "mjpeg", "-f", "image2pipe", "pipe:1"));
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
                    "-g", "30", "-keyint_min", "30", "-sc_threshold", "0",
                    "-pix_fmt", "yuv420p", "-c:a", "aac", "-ac", "2", "-b:a", "128k", "-movflags", "+frag_custom",
                    "-t", Integer.toString(config.videoSeconds() + 1),
                    "-map_metadata", "-1", "-f", "mp4", "pipe:1"));
        }
        byte[] normalizedBytes = execute(arguments, cancelled, config.productBytes() - source.length);
        if (!image) { normalizedBytes = SeekableMp4.complete(normalizedBytes); }
        content.put(image ? "detail.jpg" : "video.mp4", normalizedBytes);
        String normalizedUrl = input.add(normalizedBytes);
        byte[] thumbnail = execute(List.of(config.ffmpeg().toString(), "-nostdin", "-v", "error", "-threads", "2",
                "-protocol_whitelist", "http,tcp,pipe", "-format_whitelist", INPUT_FORMATS, "-i", normalizedUrl,
                "-frames:v", "1", "-vf", "scale='min(360,iw)':'min(360,ih)':force_original_aspect_ratio=decrease",
                "-q:v", "4", "-c:v", "mjpeg", "-f", "image2pipe", "pipe:1"), cancelled,
                config.productBytes() - source.length - normalizedBytes.length);
        content.put("thumbnail.jpg", thumbnail);
        JsonNode normalized = probe(normalizedUrl, cancelled);
        JsonNode output = normalized.path("streams").get(0);
        if (!image && normalized.path("format").path("duration").asDouble() > config.videoSeconds() + 0.1) {
            fail(ErrorCode.MEDIA_LIMIT, "Thoi luong video thuc te vuot " + config.videoSeconds() + " giay");
        }
        long bytes = content.values().stream().mapToLong(value -> value.length).sum();
        if (bytes > config.productBytes()) { fail(ErrorCode.MEDIA_LIMIT, "Tep sau xu ly vuot ngan sach luu tru san pham"); }
        return new Processed(new ProductMedia(id, 0L, kind, safeName(name), sourceType, bytes, output.path("width").asInt(),
                output.path("height").asInt(), duration, 0), Map.copyOf(content));
    }

    public record Processed(ProductMedia media, Map<String, byte[]> content) { }

    private static final String INPUT_FORMATS = "mov,matroska,webm,avi,image2,image2pipe,jpeg_pipe,png_pipe,bmp_pipe,gif,webp_pipe,apng,tiff_pipe,j2k_pipe,pbm_pipe,pgm_pipe,ppm_pipe,pam_pipe,ico,avif";

    private JsonNode probe(String source, BooleanSupplier cancelled) throws IOException {
        byte[] output = execute(List.of(config.ffprobe().toString(), "-v", "error", "-protocol_whitelist", "http,tcp,pipe",
                "-format_whitelist", INPUT_FORMATS,
                "-max_pixels", Long.toString(Math.max(config.imagePixels(), config.videoPixels())), "-show_streams", "-show_stream_groups", "-show_format",
                "-show_entries", "stream=index,codec_type,codec_name,width,height:stream_disposition=default:stream_group=index,type:stream_group_component:subcomponent:stream_group_stream=index:format=format_name,duration:format_tags=major_brand,compatible_brands",
                "-of", "json", source), cancelled, 2_097_152);
        return JSON.readTree(output);
    }

    private String imageInput(JsonNode probe, JsonNode grid) {
        if (grid == null) { return "[0:v:0]"; }
        JsonNode tiles = grid.path("subcomponents");
        int count = tiles.size(), canvasWidth = grid.path("coded_width").asInt(), canvasHeight = grid.path("coded_height").asInt();
        int width = grid.path("width").asInt(), height = grid.path("height").asInt();
        int left = grid.path("horizontal_offset").asInt(), top = grid.path("vertical_offset").asInt();
        if (count < 1 || count > 256 || width < 2 || height < 2 || left < 0 || top < 0
                || (long) left + width > canvasWidth || (long) top + height > canvasHeight) {
            fail(ErrorCode.MEDIA_INVALID, "Luoi anh HEIC/AVIF khong hop le hoac qua nhieu o");
        }
        StringBuilder inputs = new StringBuilder(), layout = new StringBuilder();
        for (JsonNode tile : tiles) {
            int index = tile.path("stream_index").asInt(-1), horizontal = tile.path("tile_horizontal_offset").asInt(-1), vertical = tile.path("tile_vertical_offset").asInt(-1);
            JsonNode tileStream = null;
            for (JsonNode candidate : probe.path("streams")) {
                if (candidate.path("index").asInt(-2) == index) { tileStream = candidate; break; }
            }
            if (tileStream == null || horizontal < 0 || vertical < 0 || !tileStream.path("codec_type").asText().equals("video")
                    || (long) horizontal + tileStream.path("width").asInt() > canvasWidth
                    || (long) vertical + tileStream.path("height").asInt() > canvasHeight) {
                fail(ErrorCode.MEDIA_INVALID, "O anh HEIC/AVIF khong hop le");
            }
            inputs.append("[0:").append(index).append(']');
            if (!layout.isEmpty()) { layout.append('|'); }
            layout.append(horizontal).append('_').append(vertical);
        }
        return inputs + (count == 1 ? "" : "xstack=inputs=" + count + ":layout=" + layout + ":fill=black,")
                + "crop=" + width + ":" + height + ":" + left + ":" + top + "[assembled];[assembled]";
    }

    private static boolean imageBrand(JsonNode probe, String... accepted) {
        JsonNode tags = probe.path("format").path("tags");
        String brands = tags.path("major_brand").asText() + tags.path("compatible_brands").asText();
        for (int offset = 0; offset + 4 <= brands.length(); offset += 4) {
            if (List.of(accepted).contains(brands.substring(offset, offset + 4))) { return true; }
        }
        return false;
    }

    private byte[] execute(List<String> arguments, BooleanSupplier cancelled, long outputLimit) throws IOException {
        if (outputLimit <= 0) { fail(ErrorCode.MEDIA_LIMIT, "Vuot ngan sach xu ly media san pham"); }
        if (!Files.isRegularFile(Path.of(arguments.get(0)))) {
            fail(ErrorCode.MEDIA_PROCESSING_FAILED, "Chua cai bo xu ly FFmpeg/ffprobe tren may server");
        }
        Process process = new ProcessBuilder(arguments).start();
        AtomicBoolean overflow = new AtomicBoolean();
        var readers = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "media-process-output"); thread.setDaemon(true); return thread;
        });
        var output = readers.submit(() -> collect(process.getInputStream(), (int) Math.min(Integer.MAX_VALUE - 8L, outputLimit), overflow));
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
