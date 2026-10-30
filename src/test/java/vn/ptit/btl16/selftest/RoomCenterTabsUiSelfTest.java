package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import vn.ptit.btl16.client.fx.FxClientController;
import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.model.ClientBid;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.network.ConnectionState;
import vn.ptit.btl16.client.network.ConnectionStateListener;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.WireMessage;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class RoomCenterTabsUiSelfTest {
    private static final Instant START = Instant.now().minusSeconds(60);
    private record View(Stage stage, FxClientController controller, ClientAppModel model) { }

    public static void main(String[] args) throws Exception {
        System.setProperty("btl16.client.manualConnect", "true");
        System.setProperty("btl16.client.sound.disabled", "true");
        Platform.startup(() -> Platform.setImplicitExit(false));
        View view = null;
        try {
            view = fx(() -> {
                Network network = new Network(); ClientAppModel model = new ClientAppModel();
                model.applyIdentity(new ApiResponse(response(MessageType.LOGIN_RESULT,
                        Map.of("success", "true", "userId", "2", "username", "Linh", "sessionToken", "center-tabs"))));
                Stage stage = new Stage();
                FxClientController controller = new FxClientController(stage, ClientConfig.loadDefault(), model,
                        network, new AccountApi(network), new AuctionApi(network));
                invoke(controller, "buildScenes");
                stage.setScene((Scene) field(controller, "mainScene")); stage.setWidth(1366); stage.setHeight(768); stage.show();
                return new View(stage, controller, model);
            });
            enter(view, 1, false);
            tabsAndProduct(view);
            chartAndJournal(view);
            compact(view);
            enter(view, 2, true);
            anonymous(view);
            System.out.println("[PASS] Room center: exclusive tabs, combined product details, hover zoom, chart tooltips, click-to-scroll/highlight, compact layout, anonymous names, room reset");
        } finally {
            View closing = view;
            if (closing != null) { fx(() -> { closing.controller().close(); closing.stage().close(); return null; }); }
            Platform.exit();
        }
    }

    private static void enter(View view, long room, boolean anonymous) throws Exception {
        fx(() -> {
            view.model().leaveJoinedAuction(); invoke(view.controller(), "renderAuctions");
            Map<String, String> data = auction(room, anonymous);
            List<ClientBid> bids = new ArrayList<>();
            for (int index = 1; index <= 12; index++) {
                long participant = index % 2 == 0 ? 2 : 3;
                String name = participant == 2 ? "Linh" : "Minh";
                if (anonymous) { participant = participant == 2 ? -1 : -2; name = participant == -1 ? "Kh\u00e1ch #1" : "Kh\u00e1ch #2"; }
                bids.add(new ClientBid(index, room, participant, name, anonymous ? "PRIVATE DISPLAY NAME" : name,
                        BigDecimal.valueOf(100000 + index * 10000), index, START.plusSeconds(index * 2L)));
            }
            view.model().applySnapshot(ClientWireParser.auction(data, ""), bids, Instant.now(), true);
            invoke(view.controller(), "renderAuctions"); layout(view); return null;
        });
        drain();
    }

    private static void tabsAndProduct(View view) throws Exception {
        fx(() -> {
            layout(view);
            VBox product = (VBox) field(view.controller(), "roomProductContent");
            TestSupport.check(tab(view, "product").isSelected() && product.isVisible(), "product tab is the room default");
            TestSupport.check(view.stage().getScene().lookup("#room-product-information") != null, "product and facts share the product tab");
            TestSupport.equals("Handcrafted ceramic vase. Original description preserved.",
                    ((Label) view.stage().getScene().lookup("#room-product-description")).getText(), "full description is preserved without a nested text area");
            Node image = (Node) field(view.controller(), "roomImage");
            Button zoom = (Button) view.stage().getScene().lookup("#room-image-zoom");
            image.fireEvent(mouse(MouseEvent.MOUSE_ENTERED));
            TestSupport.check(zoom.isVisible() && zoom.getOnAction() != null, "image hover reveals the zoom action");
            image.fireEvent(mouse(MouseEvent.MOUSE_EXITED));
            TestSupport.check(!zoom.isVisible(), "zoom overlay disappears when leaving the image");
            tab(view, "activity").fire(); layout(view);
            TestSupport.check(!product.isVisible() && !product.isManaged(), "activity tab removes product from layout");
            Node chart = view.stage().getScene().lookup("#room-price-chart");
            Node journal = view.stage().getScene().lookup("#room-events-older");
            TestSupport.check(chart.localToScene(chart.getLayoutBounds()).getMaxY() <= journal.localToScene(journal.getLayoutBounds()).getMinY(), "chart appears before journal");
            tab(view, "activity").fire();
            TestSupport.check(tab(view, "activity").isSelected(), "clicking an active center tab keeps it open");
            screenshot(view, "activity"); return null;
        });
    }

    private static void chartAndJournal(View view) throws Exception {
        fx(() -> {
            Node point = view.stage().getScene().lookup("#room-chart-bid-3");
            String tooltip = (String) point.getProperties().get("room-chart-tooltip");
            TestSupport.check(tooltip.contains("Minh") && tooltip.contains("130.000"), "chart point exposes bidder and exact amount");
            point.fireEvent(mouse(MouseEvent.MOUSE_CLICKED)); layout(view);
            TestSupport.equals(1, view.stage().getScene().getRoot().lookupAll(".selected-event").size(), "exactly one journal card is emphasized");
            TestSupport.check(view.stage().getScene().lookup("#room-event-7").getStyleClass().contains("selected-event"), "point links to its accepted bid, not another event");
            return null;
        });
        drain();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!fx(() -> selectedVisible(view)) && System.nanoTime() < deadline) { Thread.sleep(50); }
        fx(() -> {
            layout(view);
            ScrollPane scroll = (ScrollPane) field(view.controller(), "roomMainScroll");
            TestSupport.check(scroll.getVvalue() > 0, "clicking a point scrolls the center column");
            Node selected = view.stage().getScene().lookup("#room-event-7");
            var bounds = selected.localToScene(selected.getLayoutBounds());
            var viewport = scroll.lookup(".viewport").localToScene(scroll.lookup(".viewport").getLayoutBounds());
            TestSupport.check(bounds.getMinY() >= viewport.getMinY() - 2 && bounds.getMaxY() <= viewport.getMaxY() + 2, "selected card is visible in its viewport after smooth scrolling");
            TestSupport.check(tab(view, "bid").isSelected(), "center interaction does not switch the right column");
            screenshot(view, "selected-event");
            tab(view, "product").fire(); layout(view);
            TestSupport.equals(0.0, scroll.getVvalue(), "switching center tabs starts at the top");
            TestSupport.check(tab(view, "product").isSelected(), "product tab restores its content");
            screenshot(view, "product"); return null;
        });
    }

    private static void compact(View view) throws Exception {
        fx(() -> { view.stage().setWidth(1100); view.stage().setHeight(740); return null; });
        drain();
        for (String name : List.of("product", "activity")) {
            fx(() -> { tab(view, name).fire(); layout(view); return null; });
            drain();
            fx(() -> {
                layout(view);
                Node button = tab(view, name);
                var bounds = button.localToScene(button.getLayoutBounds());
                ScrollPane scroll = (ScrollPane) field(view.controller(), "roomMainScroll");
                double headerTop = bounds.getMinY(); scroll.setVvalue(1); layout(view);
                TestSupport.equals(headerTop, button.localToScene(button.getLayoutBounds()).getMinY(), "center tab buttons stay fixed while content scrolls");
                scroll.setVvalue(0); layout(view);
                Node content = name.equals("product") ? (Node) field(view.controller(), "roomProductSummary") : view.stage().getScene().lookup("#room-price-chart");
                var contentBounds = content.localToScene(content.getLayoutBounds());
                TestSupport.check(contentBounds.getMinX() >= scroll.localToScene(scroll.getLayoutBounds()).getMinX() - 1
                                && contentBounds.getMaxX() < tab(view, "bid").localToScene(tab(view, "bid").getLayoutBounds()).getMinX(),
                        "compact center content does not overlap bidding: " + name + " " + contentBounds);
                screenshot(view, "compact-" + name);
                return null;
            });
        }
    }

    private static void anonymous(View view) throws Exception {
        fx(() -> {
            TestSupport.check(tab(view, "product").isSelected(), "new room resets center selection");
            TestSupport.check(view.stage().getScene().getRoot().lookupAll(".selected-event").isEmpty(), "new room clears journal selection");
            tab(view, "activity").fire(); layout(view);
            for (Node node : view.stage().getScene().getRoot().lookupAll(".room-chart-dot")) {
                TestSupport.check(!((String) node.getProperties().get("room-chart-tooltip")).contains("PRIVATE"), "anonymous chart never uses private display names");
            }
            TestSupport.check(((String) view.stage().getScene().lookup("#room-chart-bid-3").getProperties().get("room-chart-tooltip")).contains("Kh\u00e1ch #2"), "anonymous point uses room alias");
            String labels = view.stage().getScene().getRoot().lookupAll(".room-event-row .label").stream()
                    .map(node -> ((Label) node).getText()).collect(java.util.stream.Collectors.joining(" "));
            TestSupport.check(!labels.contains("PRIVATE") && labels.contains("Kh\u00e1ch #"), "journal preserves anonymous aliases");
            screenshot(view, "anonymous"); return null;
        });
    }

    private static Map<String, String> auction(long room, boolean anonymous) {
        Map<String, String> data = new LinkedHashMap<>(Map.of("auctionId", Long.toString(room), "productId", Long.toString(room),
                "productName", "Handcrafted Ceramic Vase", "description", "Handcrafted ceramic vase. Original description preserved.",
                "hostUserId", "1", "hostUsername", "studio", "status", "OPEN", "startPrice", "100000", "currentPrice", "220000", "minBidIncrement", "10000"));
        data.put("startTime", START.toString()); data.put("endTime", START.plusSeconds(600).toString());
        data.put("anonymousGuests", Boolean.toString(anonymous)); data.put("selfParticipantId", anonymous ? "-1" : "2");
        data.put("currentWinnerId", anonymous ? "-1" : "2"); data.put("currentWinnerUsername", anonymous ? "Kh\u00e1ch #1" : "Linh");
        return data;
    }

    private static Map<String, String> events(long room) {
        Map<String, String> data = new LinkedHashMap<>(Map.of("eventCount", "25", "hasMore", "false"));
        for (int index = 0; index <= 24; index++) {
            String prefix = "event." + index + ".";
            data.put(prefix + "id", Integer.toString(index + 1)); data.put(prefix + "time", START.plusSeconds(index).toString());
            boolean bid = index > 0 && index % 2 == 0;
            int bidId = index / 2;
            data.put(prefix + "type", index == 0 ? "CREATED" : bid ? "BID_ACCEPTED" : "JOINED");
            data.put(prefix + "username", room == 2 ? bidId % 2 == 0 ? "Kh\u00e1ch #1" : "Kh\u00e1ch #2" : bidId % 2 == 0 ? "Linh" : "Minh");
            if (bid) { data.put(prefix + "amount", Integer.toString(100000 + bidId * 10000)); }
        }
        return data;
    }

    private static ToggleButton tab(View view, String name) { return (ToggleButton) view.stage().getScene().lookup("#room-tab-" + name); }
    private static boolean selectedVisible(View view) throws Exception {
        layout(view);
        ScrollPane scroll = (ScrollPane) field(view.controller(), "roomMainScroll");
        Node selected = view.stage().getScene().lookup("#room-event-7");
        var bounds = selected.localToScene(selected.getLayoutBounds());
        Node viewportNode = scroll.lookup(".viewport");
        var viewport = viewportNode.localToScene(viewportNode.getLayoutBounds());
        return bounds.getMinY() >= viewport.getMinY() - 2 && bounds.getMaxY() <= viewport.getMaxY() + 2;
    }
    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type) {
        return new MouseEvent(type, 1, 1, 1, 1, MouseButton.PRIMARY, 1, false, false, false, false, false, false, false, false, false, false, null);
    }
    private static void layout(View view) { view.stage().getScene().getRoot().applyCss(); view.stage().getScene().getRoot().layout(); }
    private static Object field(Object target, String name) throws Exception { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static void invoke(Object target, String name) throws Exception { var method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(target); }
    private static <Result> Result fx(Callable<Result> action) throws Exception {
        CompletableFuture<Result> result = new CompletableFuture<>();
        Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } });
        return result.get(30, TimeUnit.SECONDS);
    }
    private static void drain() throws Exception { fx(() -> null); fx(() -> null); }
    private static WireMessage response(MessageType type, Map<String, String> values) {
        Map<String, String> data = new LinkedHashMap<>(values); data.put("success", "true");
        return WireMessage.response(type, null, 1, data);
    }
    private static void screenshot(View view, String name) throws Exception {
        var image = view.stage().getScene().snapshot(null);
        var bitmap = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < bitmap.getHeight(); row++) {
            for (int column = 0; column < bitmap.getWidth(); column++) { bitmap.setRGB(column, row, image.getPixelReader().getArgb(column, row)); }
        }
        Path output = Path.of("target", "room-center-preview"); Files.createDirectories(output);
        javax.imageio.ImageIO.write(bitmap, "png", output.resolve(name + ".png").toFile());
    }
    private static final class Network implements ClientTransport {
        @Override public CompletableFuture<WireMessage> sendRequest(MessageType type, Map<String, String> data) {
            long room = Long.parseLong(data.getOrDefault("auctionId", "1"));
            return switch (type) {
                case GET_ROOM_EVENTS -> CompletableFuture.completedFuture(response(MessageType.ROOM_EVENTS_RESULT, events(room)));
                case GET_AUCTION_STATS -> CompletableFuture.completedFuture(response(MessageType.AUCTION_STATS_RESULT,
                        Map.of("totalBids", "12", "uniqueBidders", "2", "extensionCount", "0", "extensionsComplete", "true")));
                case GET_ROOM_SOCIAL -> CompletableFuture.completedFuture(response(type, Map.of("memberCount", "0", "chatCount", "0")));
                default -> new CompletableFuture<>();
            };
        }
        @Override public CompletableFuture<Void> connect() { return CompletableFuture.completedFuture(null); }
        @Override public void disconnect() { }
        @Override public boolean isConnected() { return true; }
        @Override public ConnectionState getState() { return ConnectionState.CONNECTED; }
        @Override public String getEndpoint() { return "test"; }
        @Override public void setEndpoint(String endpoint) { }
        @Override public CompletableFuture<WireMessage> ping() { return new CompletableFuture<>(); }
        @Override public void addEventListener(Consumer<WireMessage> listener) { }
        @Override public void addStateListener(ConnectionStateListener listener) { }
        @Override public void close() { }
    }
}
