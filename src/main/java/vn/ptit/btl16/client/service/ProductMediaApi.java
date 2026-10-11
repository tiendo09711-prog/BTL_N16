package vn.ptit.btl16.client.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.network.ClientTransport;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

public final class ProductMediaApi {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
    private final URI base;
    private final Supplier<String> token;

    public ProductMediaApi(URI base, Supplier<String> token) { this.base = base; this.token = token; }

    public static CompletableFuture<ProductMediaApi> connect(AuctionApi api, ClientTransport transport, ClientAppModel model) {
        return api.mediaEndpoint().thenApply(response -> {
            if (!response.isSuccess()) { throw new CompletionException(new IOException(response.getMessageText())); }
            String endpoint = transport.getEndpoint();
            URI address = URI.create(endpoint.contains("://") ? endpoint : "tcp://" + endpoint);
            try {
                URI base = new URI("http", null, address.getHost(), Integer.parseInt(response.get("mediaPort")), "/", null, null);
                return new ProductMediaApi(base, model::getSessionToken);
            } catch (Exception exception) { throw new CompletionException(exception); }
        });
    }

    public String url(String relative) { return base.resolve(relative).toString(); }
    public CompletableFuture<JsonNode> manifest(long productId, long auctionId) { return request("GET", "/v1/products/" + productId + "?auctionId=" + auctionId, null); }
    public CompletableFuture<JsonNode> limits() { return request("GET", "/v1/limits", null); }
    public CompletableFuture<String> begin(long productId) { return request("POST", "/v1/drafts?productId=" + productId, Map.of()).thenApply(body -> body.path("draftId").asText()); }
    public CompletableFuture<JsonNode> cancel(String draft) { return request("DELETE", "/v1/drafts/" + draft, null); }
    public CompletableFuture<JsonNode> remove(String draft, String id) { return request("DELETE", "/v1/drafts/" + draft + "/files/" + id, null); }
    public CompletableFuture<JsonNode> commit(String draft, String code, String name, String description, List<String> mediaIds) {
        return request("POST", "/v1/drafts/" + draft + "/commit", Map.of("code", code, "name", name, "description", description, "mediaIds", mediaIds));
    }
    public CompletableFuture<JsonNode> transform(String draft, String id, int turns, double left, double top, double width, double height) {
        return request("POST", "/v1/drafts/" + draft + "/transform", Map.of("id", id, "turns", turns, "left", left, "top", top, "width", width, "height", height));
    }
    public CompletableFuture<JsonNode> upload(String draft, Path file, String kind, DoubleConsumer progress) {
        try {
            HttpRequest.BodyPublisher fileBody = HttpRequest.BodyPublishers.ofFile(file);
            HttpRequest.BodyPublisher publisher = new HttpRequest.BodyPublisher() {
                public long contentLength() { return fileBody.contentLength(); }
                public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
                    fileBody.subscribe(new Flow.Subscriber<>() {
                        private long bytes;
                        public void onSubscribe(Flow.Subscription subscription) { subscriber.onSubscribe(subscription); }
                        public void onNext(ByteBuffer buffer) { bytes += buffer.remaining(); progress.accept(Math.min(1, bytes / (double) contentLength())); subscriber.onNext(buffer); }
                        public void onError(Throwable error) { subscriber.onError(error); }
                        public void onComplete() { subscriber.onComplete(); }
                    });
                }
            };
            return send(HttpRequest.newBuilder(base.resolve("/v1/drafts/" + draft + "/files?kind=" + kind + "&name=" + URLEncoder.encode(file.getFileName().toString(), StandardCharsets.UTF_8)))
                    .header("Content-Type", "application/octet-stream").POST(publisher));
        } catch (IOException exception) { return CompletableFuture.failedFuture(exception); }
    }
    private CompletableFuture<JsonNode> request(String method, String path, Object body) {
        try {
            HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body));
            return send(HttpRequest.newBuilder(base.resolve(path)).header("Content-Type", "application/json").method(method, publisher));
        } catch (IOException exception) { return CompletableFuture.failedFuture(exception); }
    }
    private CompletableFuture<JsonNode> send(HttpRequest.Builder request) {
        var network = HTTP.sendAsync(request.timeout(Duration.ofMinutes(10)).header("Authorization", "Bearer " + token.get()).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        CompletableFuture<JsonNode> result = new CompletableFuture<>() {
            @Override public boolean cancel(boolean interrupt) { network.cancel(interrupt); return super.cancel(interrupt); }
        };
        network.whenComplete((response, error) -> {
            if (error != null) { result.completeExceptionally(error); return; }
            try {
                JsonNode data = JSON.readTree(response.body());
                if (response.statusCode() != 200) { throw new IOException(data.path("message").asText("Khong the tai media") + " [" + data.path("errorCode").asText() + "]"); }
                result.complete(data);
            } catch (Exception exception) { result.completeExceptionally(exception); }
        });
        return result;
    }
    public static String message(Throwable error) {
        while (error.getCause() != null) { error = error.getCause(); }
        return error.getMessage() == null ? "Khong the ket noi kenh media. Kiem tra cong media tren server." : error.getMessage();
    }
}
