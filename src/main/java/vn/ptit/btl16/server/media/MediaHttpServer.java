package vn.ptit.btl16.server.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.session.UserSession;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class MediaHttpServer implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final MediaService service;
    private final String bind;
    private HttpServer server;
    private final java.util.concurrent.Semaphore downloads = new java.util.concurrent.Semaphore(12);
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(24, 24, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(48), task -> { Thread thread = new Thread(task, "media-http"); thread.setDaemon(true); return thread; },
            new ThreadPoolExecutor.AbortPolicy());

    public MediaHttpServer(MediaService service, String bind) { this.service = service; this.bind = bind; }

    public void start() throws IOException {
        if (System.getProperty("sun.net.httpserver.maxReqTime") == null) {
            System.setProperty("sun.net.httpserver.maxReqTime", Integer.toString(service.config().timeoutSeconds()));
        }
        if (System.getProperty("sun.net.httpserver.timerMillis") == null) { System.setProperty("sun.net.httpserver.timerMillis", "250"); }
        server = HttpServer.create(new InetSocketAddress(bind, service.config().port()), 32);
        server.createContext("/v1/", this::handle); server.setExecutor(executor); server.start();
        service.setPort(server.getAddress().getPort());
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            exchange.getResponseHeaders().set("Cache-Control", "private, no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
            String[] path = exchange.getRequestURI().getPath().split("/");
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            String method = exchange.getRequestMethod();
            if (path.length == 5 && path[2].equals("files") && (method.equals("GET") || method.equals("HEAD"))) {
                if (!downloads.tryAcquire()) { reply(exchange, 429, Map.of("errorCode", "MEDIA_LIMIT", "message", "Nhieu luot tai media; hay thu lai")); return; }
                try { stream(exchange, path[3], path[4], query.get("ticket")); } finally { downloads.release(); }
                return;
            }
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            UserSession session = service.authenticate(authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null);
            if (path.length == 3 && path[2].equals("limits") && method.equals("GET")) { reply(exchange, 200, service.limits()); return; }
            if (path.length == 4 && path[2].equals("products") && method.equals("GET")) {
                reply(exchange, 200, service.manifest(session, Long.parseLong(path[3]), Long.parseLong(query.getOrDefault("auctionId", "0")))); return;
            }
            if (path.length == 3 && path[2].equals("drafts") && method.equals("POST")) {
                reply(exchange, 200, Map.of("draftId", service.begin(session, Long.parseLong(query.getOrDefault("productId", "0"))))); return;
            }
            if (path.length >= 4 && path[2].equals("drafts")) {
                String id = path[3];
                if (path.length == 4 && method.equals("DELETE")) { service.cancel(session, id); reply(exchange, 200, Map.of("cancelled", true)); return; }
                if (path.length == 5 && path[4].equals("files") && method.equals("POST")) {
                    String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
                    long size = contentLength == null ? -1 : Long.parseLong(contentLength);
                    ProductMedia media = service.upload(session, id, query.getOrDefault("kind", "IMAGE"), query.getOrDefault("name", "media"),
                            exchange.getRequestBody(), size, 0, 0, 0, 1, 1);
                    reply(exchange, 200, service.uploaded(session, id, media)); return;
                }
                if (path.length == 6 && path[4].equals("files") && method.equals("DELETE")) {
                    service.remove(session, id, path[5]); reply(exchange, 200, Map.of("removed", true)); return;
                }
                if (path.length == 5 && path[4].equals("transform") && method.equals("POST")) {
                    JsonNode body = body(exchange);
                    ProductMedia media = service.transform(session, id, body.path("id").asText(), body.path("turns").asInt(),
                            body.path("left").asDouble(), body.path("top").asDouble(), body.path("width").asDouble(1), body.path("height").asDouble(1));
                    reply(exchange, 200, service.uploaded(session, id, media)); return;
                }
                if (path.length == 5 && path[4].equals("commit") && method.equals("POST")) {
                    JsonNode body = body(exchange); var ids = new ArrayList<String>();
                    for (JsonNode item : body.path("mediaIds")) { ids.add(item.asText()); }
                    var product = service.commit(session, id, body.path("code").asText(), body.path("name").asText(), body.path("description").asText(), ids);
                    reply(exchange, 200, Map.of("productId", product.getProductId())); return;
                }
            }
            reply(exchange, 404, Map.of("errorCode", "MEDIA_INVALID", "message", "Yeu cau media khong ton tai"));
        } catch (AuctionException exception) {
            if (exchange.getResponseCode() != -1) { return; }
            reply(exchange, exception.getErrorCode() == ErrorCode.AUTH_REQUIRED ? 401 : 400,
                    Map.of("errorCode", exception.getErrorCode().name(), "message", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            if (exchange.getResponseCode() != -1) { return; }
            reply(exchange, 400, Map.of("errorCode", "MEDIA_INVALID", "message", "Yeu cau media khong hop le"));
        } catch (Exception exception) {
            if (exchange.getResponseCode() != -1) { return; }
            java.util.logging.Logger.getLogger(MediaHttpServer.class.getName()).log(java.util.logging.Level.WARNING, "Media request failed", exception);
            reply(exchange, 500, Map.of("errorCode", "MEDIA_PROCESSING_FAILED", "message", "Khong the xu ly media. Hay thu lai hoac kiem tra kho luu tru server"));
        } finally { exchange.close(); }
    }

    private void stream(HttpExchange exchange, String id, String variant, String ticket) throws IOException {
        var file = service.readable(id, variant, ticket); long size = Files.size(file);
        long start = 0; long end = size - 1; int status = 200;
        String range = exchange.getRequestHeaders().getFirst("Range");
        if (range != null) {
            try {
                if (!range.matches("bytes=\\d*-\\d*")) { throw new IllegalArgumentException(); }
                String[] bounds = range.substring(6).split("-", -1);
                if (bounds[0].isEmpty()) { long suffix = Long.parseLong(bounds[1]); if (suffix <= 0) { throw new IllegalArgumentException(); } start = Math.max(0, size - suffix); }
                else { start = Long.parseLong(bounds[0]); if (!bounds[1].isEmpty()) { end = Math.min(end, Long.parseLong(bounds[1])); } }
                if (start < 0 || start > end || start >= size) { throw new IllegalArgumentException(); }
                status = 206;
            } catch (IllegalArgumentException exception) {
                exchange.getResponseHeaders().set("Content-Range", "bytes */" + size); exchange.sendResponseHeaders(416, -1); return;
            }
        }
        exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
        exchange.getResponseHeaders().set("Content-Type", variant.equals("video") ? "video/mp4" : variant.equals("original") ? "application/octet-stream" : "image/jpeg");
        if (variant.equals("original")) { exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=original"); }
        if (status == 206) { exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + size); }
        exchange.getResponseHeaders().set("Content-Length", Long.toString(end - start + 1));
        if (exchange.getRequestMethod().equals("HEAD")) { exchange.sendResponseHeaders(status, -1); return; }
        exchange.sendResponseHeaders(status, end - start + 1);
        try (FileChannel channel = FileChannel.open(file); var output = exchange.getResponseBody()) {
            channel.position(start); long remaining = end - start + 1; ByteBuffer buffer = ByteBuffer.allocate(65536);
            while (remaining > 0) {
                service.readable(id, variant, ticket);
                buffer.clear(); buffer.limit((int) Math.min(buffer.capacity(), remaining)); int count = channel.read(buffer);
                if (count < 0) { break; } output.write(buffer.array(), 0, count); remaining -= count;
            }
        }
    }

    private static JsonNode body(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(65537);
        if (bytes.length > 65536) { throw new IllegalArgumentException("Request too large"); }
        return JSON.readTree(bytes);
    }
    private static Map<String, String> query(String raw) {
        Map<String, String> result = new HashMap<>();
        if (raw != null) { for (String pair : raw.split("&")) { String[] parts = pair.split("=", 2); result.put(decode(parts[0]), parts.length == 2 ? decode(parts[1]) : ""); } }
        return result;
    }
    private static String decode(String value) { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
    private static void reply(HttpExchange exchange, int status, Object data) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(data); exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes);
    }
    @Override public void close() { if (server != null) { server.stop(0); } executor.shutdownNow(); service.close(); }
}
