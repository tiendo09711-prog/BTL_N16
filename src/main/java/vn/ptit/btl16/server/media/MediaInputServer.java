package vn.ptit.btl16.server.media;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class MediaInputServer implements AutoCloseable {
    private final Map<String, byte[]> content = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "media-memory-input"); thread.setDaemon(true); return thread;
    });
    private final HttpServer server;

    MediaInputServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 8);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try {
                byte[] bytes = content.get(exchange.getRequestURI().getPath());
                String method = exchange.getRequestMethod();
                if (bytes == null || (!method.equals("GET") && !method.equals("HEAD"))) {
                    exchange.sendResponseHeaders(404, -1); return;
                }
                long start = 0, end = bytes.length - 1L;
                String range = exchange.getRequestHeaders().getFirst("Range");
                if (range != null) {
                    try {
                        if (!range.matches("bytes=\\d+-\\d*")) { throw new IllegalArgumentException(); }
                        String[] bounds = range.substring(6).split("-", -1);
                        start = Long.parseLong(bounds[0]);
                        if (!bounds[1].isEmpty()) { end = Math.min(end, Long.parseLong(bounds[1])); }
                        if (start > end || start >= bytes.length) { throw new IllegalArgumentException(); }
                    } catch (IllegalArgumentException exception) {
                        exchange.getResponseHeaders().set("Content-Range", "bytes */" + bytes.length);
                        exchange.sendResponseHeaders(416, -1); return;
                    }
                    exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + bytes.length);
                }
                exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
                exchange.getResponseHeaders().set("Connection", "close");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.getResponseHeaders().set("Content-Length", Long.toString(end - start + 1));
                exchange.sendResponseHeaders(range == null ? 200 : 206, method.equals("HEAD") ? -1 : end - start + 1);
                if (!method.equals("HEAD")) { exchange.getResponseBody().write(bytes, (int) start, (int) (end - start + 1)); }
            } finally { exchange.close(); }
        });
        server.start();
    }

    String add(byte[] bytes) {
        String path = "/" + UUID.randomUUID();
        content.put(path, bytes);
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @Override public void close() { server.stop(0); content.clear(); executor.shutdownNow(); }
}
