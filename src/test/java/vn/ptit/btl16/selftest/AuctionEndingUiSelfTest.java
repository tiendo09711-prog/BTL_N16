package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.stage.*;
import vn.ptit.btl16.client.fx.*;
import vn.ptit.btl16.client.model.*;
import vn.ptit.btl16.client.network.*;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.protocol.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class AuctionEndingUiSelfTest {
    private static final PseudoClass ENDED = PseudoClass.getPseudoClass("auction-ended"), DIM = PseudoClass.getPseudoClass("flash-dim");
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        var network = new Network(); var model = new ClientAppModel();
        FxClientController controller = fx(() -> {
            model.applyIdentity(new ApiResponse(response(MessageType.LOGIN_RESULT, Map.of("userId", "2", "username", "alice", "sessionToken", "session"))));
            Stage stage = new Stage();
            var value = new FxClientController(stage, ClientConfig.loadDefault(), model, network, new AccountApi(network), new AuctionApi(network));
            invoke(value, "buildScenes"); stage.setScene((Scene) field(value, "mainScene")); stage.setWidth(1200); stage.setHeight(780); stage.show();
            return value;
        });
        try {
            var scene = fx(() -> (Scene) field(controller, "mainScene"));
            var gallery = fx(() -> (AuctionMarketplacePane) field(controller, "auctionCards"));
            var celebration = fx(() -> (WinnerCelebration) field(controller, "winnerCelebration"));
            var now = new AtomicReference<>(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
            Map<String, String> ended = data(1, "ENDED");
            fx(() -> {
                TestSupport.check(scene.lookup("#marketplace-phase-ended") == null
                        && scene.getRoot().lookupAll(".market-phase-tabs .toggle-button").size() == 3,
                        "lobby contains only all-products, live and upcoming tabs");
                gallery.configure(ignored -> CompletableFuture.completedFuture(null), now::get);
                model.applySnapshot(ClientWireParser.auction(data(2, "OPEN"), ""), List.of(), Instant.now(), false);
                event(controller, ended);
                TestSupport.check(!celebration.isVisible(), "loser does not see winner artwork");
                TestSupport.check(((ImageView) celebration.lookup("#winner-celebration-image")).getImage() == null, "non-winners never load GIF frames");
                model.replaceAuctions(List.of(ClientWireParser.auction(data(2, "OPEN"), "")), Instant.now()); invoke(controller, "renderAuctions");
                TestSupport.equals(2, gallery.getItems().size(), "open-only list refresh preserves exterior ended card");
                ((Button) gallery.lookup("#auction-card-1")).fire();
                TestSupport.check(((Button) field(controller, "joinButton")).isDisabled(), "ended card cannot rejoin");
                gallery.refresh(); scene.getRoot().applyCss(); scene.getRoot().layout();
                Label time = (Label) gallery.lookup("#auction-card-1 .product-card-status");
                TestSupport.check(time.getPseudoClassStates().contains(ENDED) && !time.getPseudoClassStates().contains(DIM), "exterior time bright phase");
                TestSupport.equals(Color.web("#b91c1c"), time.getTextFill(), "ended time is red");
                now.set(now.get().plusMillis(500)); gallery.refresh(); scene.getRoot().applyCss();
                TestSupport.check(time.getPseudoClassStates().contains(DIM) && time.getOpacity() < .5, "exterior time flashes every half second");
                TestSupport.check(((Label) field(controller, "detailTime")).getPseudoClassStates().contains(ENDED), "selected room time is red too");
                var live = new LiveAuctionRoom(); live.update(ClientWireParser.auction(ended, ""), List.of(), 2, true, now.get());
                TestSupport.check(((Label) field(live, "deadline")).getPseudoClassStates().contains(ENDED), "inside live deadline flashes too");
                live.update(ClientWireParser.auction(data(2, "OPEN"), ""), List.of(), 2, true, now.get());
                TestSupport.check(!((Label) field(live, "deadline")).getPseudoClassStates().contains(ENDED), "open/extended room resets flashing");
                ((Button) scene.lookup("#product-ranking-navigation")).fire(); return null;
            }); drain();
            fx(() -> {
                Stage ranking = Window.getWindows().stream().filter(window -> window instanceof Stage stage && "Top doanh thu".equals(stage.getTitle())).map(window -> (Stage) window).findFirst().orElseThrow();
                TestSupport.check(ranking.getScene().lookup("#revenue-ranking-1") != null, "ranking navigation loads seller revenue row");
                TestSupport.check(ranking.getScene().getRoot().lookupAll(".ranking-price").stream().anyMatch(node -> ((Label) node).getText().contains("1.500.000")), "ranking formats exact price");
                ranking.close(); return null;
            });
            Map<String, String> win = data(2, "ENDED"); win.putAll(Map.of("winnerId", "-7", "winnerUsername", "Khách #7", "winnerIsSelf", "true", "anonymousGuests", "true", "currentWinnerId", "-7"));
            fx(() -> {
                event(controller, win); scene.getRoot().applyCss(); scene.getRoot().layout();
                TestSupport.check(celebration.isVisible(), "anonymous winner outside room sees celebration");
                ImageView image = (ImageView) celebration.lookup("#winner-celebration-image");
                TestSupport.check(image.getImage() != null && !image.getImage().isError()
                        && image.getImage().getUrl().endsWith("/ui/winner/badge.gif"), "badge from the new supplied GIF loads");
                Node frame = celebration.lookup("#winner-celebration-frame"); var bounds = frame.localToScene(frame.getLayoutBounds());
                double area = bounds.getWidth() * bounds.getHeight() / (scene.getWidth() * scene.getHeight());
                TestSupport.check(Math.abs(area - 1.0 / 3) < .01, "center badge occupies one third of interface area");
                TestSupport.check(Math.abs((bounds.getMinX() + bounds.getMaxX()) / 2 - scene.getWidth() / 2) < 2
                        && Math.abs((bounds.getMinY() + bounds.getMaxY()) / 2 - scene.getHeight() / 2) < 2, "artwork centered on full interface");
                ImageView fireworks = (ImageView) celebration.lookup("#winner-celebration-fireworks");
                TestSupport.check(fireworks.getImage() != null && !fireworks.getImage().isError()
                        && fireworks.getImage().getUrl().endsWith("/ui/winner/fireworks.gif"), "surrounding fireworks from supplied GIF load separately");
                var fireworksBounds = fireworks.localToScene(fireworks.getLayoutBounds());
                TestSupport.check(fireworksBounds.getWidth() > scene.getWidth() * .95 && fireworksBounds.getHeight() > scene.getHeight() * .95
                        && fireworksBounds.getMinX() >= 0 && fireworksBounds.getMaxX() <= scene.getWidth()
                        && fireworksBounds.getMinY() >= 0 && fireworksBounds.getMaxY() <= scene.getHeight(), "fireworks cover nearly the whole window");
                TestSupport.check(fireworks.isMouseTransparent() && image.isMouseTransparent(), "animation layers do not intercept user input");
                return null;
            });
            var firstImage = fx(() -> ((ImageView) celebration.lookup("#winner-celebration-image")).getImage());
            long firstFrame = fx(() -> fingerprint(celebration.lookup("#winner-celebration-image")));
            long firstFireworks = fx(() -> fingerprint(celebration.lookup("#winner-celebration-fireworks")));
            Thread.sleep(900);
            fx(() -> {
                TestSupport.check(celebration.isVisible() && fingerprint(celebration.lookup("#winner-celebration-image")) != firstFrame,
                        "fireworks animate while the celebration is visible");
                TestSupport.check(fingerprint(celebration.lookup("#winner-celebration-fireworks")) != firstFireworks, "full-window fireworks animate independently of badge size");
                var parameters = new javafx.scene.SnapshotParameters(); parameters.setFill(Color.TRANSPARENT);
                var transparent = celebration.lookup("#winner-celebration-fireworks").snapshot(parameters, null);
                TestSupport.check(transparent.getPixelReader().getColor(0, 0).getOpacity() == 0, "GIF transparency removes the magenta preview background");
                screenshot(scene.getRoot(), "target/auction-winner-wide-fireworks.png");
                Node frame = celebration.lookup("#winner-celebration-frame"); Button close = (Button) celebration.lookup("#winner-celebration-close");
                var frameBounds = frame.localToScene(frame.getLayoutBounds()); var closeBounds = close.localToScene(close.getLayoutBounds());
                TestSupport.check(Math.abs(closeBounds.getMaxX() - frameBounds.getMaxX()) < 2
                        && Math.abs(closeBounds.getMinY() - frameBounds.getMinY()) < 2, "close button stays at upper-right corner of GIF");
                ((Button) celebration.lookup("#winner-celebration-close")).fire();
                TestSupport.check(!celebration.isVisible(), "close button dismisses immediately"); event(controller, win);
                TestSupport.check(((ImageView) celebration.lookup("#winner-celebration-image")).getImage() == null, "dismissal releases animated image");
                TestSupport.check(((ImageView) celebration.lookup("#winner-celebration-fireworks")).getImage() == null, "close button releases surrounding fireworks too");
                TestSupport.check(!celebration.isVisible(), "duplicate does not reopen dismissed image");
                Map<String, String> noWinner = data(3, "ENDED"); noWinner.put("winnerId", ""); noWinner.put("winnerIsSelf", "true");
                TestSupport.check(!celebration.announce(noWinner, 2), "no-bid result never celebrates");
                Map<String, String> timed = new LinkedHashMap<>(win); timed.put("auctionId", "4"); celebration.announce(timed, 2);
                TestSupport.check(((ImageView) celebration.lookup("#winner-celebration-image")).getImage() != firstImage, "next win loads a fresh animation from the beginning");
                return null;
            });
            Thread.sleep(1700); TestSupport.check(fx(celebration::isVisible), "image stays visible before three seconds");
            TestSupport.waitUntil(() -> { try { return fx(() -> !celebration.isVisible()); } catch (Exception error) { throw new RuntimeException(error); } }, 2200, "image auto dismisses after three seconds");
            fx(() -> {
                TestSupport.check(((ImageView) celebration.lookup("#winner-celebration-image")).getImage() == null, "timeout releases animated image");
                TestSupport.check(((ImageView) celebration.lookup("#winner-celebration-fireworks")).getImage() == null, "timeout releases fireworks layer");
                var next = new LinkedHashMap<>(win); next.put("auctionId", "5"); celebration.announce(next, 2); celebration.clear();
                TestSupport.check(!celebration.isVisible(), "session cleanup removes pending artwork");
                model.updateServerClock(Instant.now().plusSeconds(61)); invoke(controller, "refreshClock");
                TestSupport.check(gallery.getItems().isEmpty(), "exterior ended cards leave after result retention"); return null;
            });
            System.out.println("[PASS] AuctionEndingUiSelfTest: exterior retention, red flashing/reset, Top 10 UI, winner-only new GIF, centered one-third badge, near-full-window fireworks, transparency/animation, top-right close/dedup, fresh animation per win, three-second timeout and both-layer cleanup");
        } finally { fx(() -> { controller.close(); for (Window window : List.copyOf(Window.getWindows())) { if (window instanceof Stage stage) { stage.close(); } } return null; }); Platform.exit(); }
    }
    private static Map<String, String> data(long id, String status) {
        Instant now = Instant.now(); var result = new LinkedHashMap<String, String>();
        result.putAll(Map.of("auctionId", Long.toString(id), "productId", Long.toString(id), "productName", "Bình gốm cổ", "productCode", "SP_" + id,
                "status", status, "hostUserId", "1", "hostUsername", "demo", "version", status.equals("OPEN") ? "1" : "2"));
        result.putAll(Map.of("startPrice", "100000", "minBidIncrement", "10000", "currentPrice", "1500000", "currentWinnerId", "3", "currentWinnerUsername", "Bob",
                "winnerId", "3", "winnerUsername", "Bob", "finalPrice", "1500000", "winnerIsSelf", "false"));
        result.putAll(Map.of("startTime", now.minusSeconds(60).toString(), "endTime", status.equals("OPEN") ? now.plusSeconds(120).toString() : now.minusSeconds(1).toString(),
                "endedAt", status.equals("OPEN") ? "" : now.toString(), "serverNow", now.toString(), "visibility", "PUBLIC")); return result;
    }
    private static void event(FxClientController controller, Map<String, String> data) throws Exception { invoke(controller, "handleServerEvent", new Class<?>[]{WireMessage.class}, WireMessage.event(MessageType.AUCTION_ENDED, 1, data)); }
    private static WireMessage response(MessageType type, Map<String, String> data) { var result = new LinkedHashMap<>(data); result.put("success", "true"); return WireMessage.response(type, null, 1, result); }
    private static Object field(Object target, String name) throws Exception { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static void invoke(Object target, String name) throws Exception { invoke(target, name, new Class<?>[0]); }
    private static void invoke(Object target, String name, Class<?>[] types, Object... values) throws Exception { var method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(target, values); }
    private static <T> T fx(Callable<T> action) throws Exception { var result = new CompletableFuture<T>(); Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } }); return result.get(45, TimeUnit.SECONDS); }
    private static void drain() throws Exception { fx(() -> null); fx(() -> null); }
    private static void screenshot(Node node, String file) throws Exception {
        var pixels = node.snapshot(null, null); var output = new java.awt.image.BufferedImage((int) pixels.getWidth(), (int) pixels.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) { for (int x = 0; x < output.getWidth(); x++) { output.setRGB(x, y, pixels.getPixelReader().getArgb(x, y)); } } javax.imageio.ImageIO.write(output, "png", new java.io.File(file));
    }
    private static long fingerprint(Node node) {
        var parameters = new javafx.scene.SnapshotParameters(); parameters.setFill(Color.TRANSPARENT);
        var image = node.snapshot(parameters, null); long hash = 1;
        for (int y = 0; y < image.getHeight(); y += 3) { for (int x = 0; x < image.getWidth(); x += 3) { hash = hash * 31 + image.getPixelReader().getArgb(x, y); } }
        return hash;
    }
    private static final class Network implements ClientTransport {
        @Override public CompletableFuture<WireMessage> sendRequest(MessageType type, Map<String,String> data) {
            if (type == MessageType.GET_REVENUE_RANKING) { return CompletableFuture.completedFuture(response(MessageType.REVENUE_RANKING_RESULT, Map.of("count", "1", "ranking.0.username", "nguoiban", "ranking.0.displayName", "Người bán", "ranking.0.sellerId", "1", "ranking.0.revenue", "1500000", "ranking.0.saleCount", "2", "ranking.0.discountRate", "0.05"))); }
            return CompletableFuture.completedFuture(response(type, Map.of("count", "0", "userId", "2", "balance", "10000000", "available", "10000000", "reserved", "0", "entryCount", "0", "memberCount", "0", "chatCount", "0")));
        }
        @Override public CompletableFuture<Void> connect() { return CompletableFuture.completedFuture(null); }
        @Override public void disconnect() { }
        @Override public boolean isConnected() { return true; }
        @Override public ConnectionState getState() { return ConnectionState.CONNECTED; }
        @Override public String getEndpoint() { return "test"; }
        @Override public void setEndpoint(String endpoint) { }
        @Override public CompletableFuture<WireMessage> ping() { return sendRequest(MessageType.PING, Map.of()); }
        @Override public void addEventListener(Consumer<WireMessage> listener) { }
        @Override public void addStateListener(ConnectionStateListener listener) { }
        @Override public void close() { }
    }
}
