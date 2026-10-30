package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import vn.ptit.btl16.client.fx.LiveAuctionRoom;
import vn.ptit.btl16.client.model.ClientAuction;
import vn.ptit.btl16.client.model.ClientBid;
import vn.ptit.btl16.common.fx.DesktopTheme;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class LiveAuctionRoomUiSelfTest {
    private static final Instant START = Instant.parse("2026-10-24T10:00:00Z");

    private LiveAuctionRoomUiSelfTest() { }

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            fx(() -> { verify(); return null; });
            System.out.println("[PASS] LiveAuctionRoomUiSelfTest: real history/chart, own-bid filter, presence, leader/outbid, deadline/extension, authoritative result, room reset, compact layout");
        } finally { Platform.exit(); }
    }

    private static void verify() throws Exception {
        LiveAuctionRoom room = new LiveAuctionRoom();
        HBox columns = new HBox(16, room.insights(), room.history());
        HBox.setHgrow(room.insights(), Priority.ALWAYS);
        room.history().setPrefWidth(370);
        room.history().setMinWidth(330);
        room.history().setMaxWidth(400);
        BorderPane root = new BorderPane(columns);
        root.setTop(room.heading());
        root.setStyle("-fx-padding: 20;");
        Scene scene = new Scene(root, 1100, 700);
        DesktopTheme.apply(scene);
        AtomicInteger historyOpens = new AtomicInteger();
        room.onHistory(historyOpens::incrementAndGet);
        ClientAuction empty = auction(1, "100000", null, "", "OPEN", START.plusSeconds(300));
        room.update(empty, List.of(), 2, true, START.plusSeconds(30));
        layout(root);
        TestSupport.equals("...", label(root, "room-recent-bids"), "statistics stay unknown until server responds");
        TestSupport.equals(1, chart(root).getData().get(0).getData().size(), "only opening price is plotted before the first bid");
        TestSupport.check(label(root, "room-session-status").contains("m\u1edf gi\u00e1"), "waiting state");
        TestSupport.equals("4 k\u1ebft n\u1ed1i theo d\u00f5i", label(root, "room-viewers"), "server watcher connections are not unique people");
        TestSupport.check(Math.abs(progress(root) - 0.9) < 0.0001, "remaining progress uses server time");

        ClientBid first = bid(1, 2, "linh", "110000", 10);
        ClientBid second = bid(2, 3, "minh", "130000", 20);
        ClientBid third = bid(3, 2, "linh", "150000", 30);
        ClientBid otherRoom = new ClientBid(99, 99, 9, "other", new BigDecimal("999999"), 99, START);
        List<ClientBid> history = List.of(third, otherRoom, first, second);
        ClientAuction leading = auction(1, "150000", 2L, "linh", "OPEN", START.plusSeconds(300));
        room.update(leading, history, 2, true, START.plusSeconds(60));
        room.setStatistics(java.util.Map.of("totalBids", "125", "uniqueBidders", "7", "extensionCount", "4", "extensionsComplete", "true", "priceIncrease", "50000"));
        layout(root);
        TestSupport.equals("125", label(root, "room-recent-bids"), "server total exceeds three loaded bids");
        TestSupport.equals("7", label(root, "room-recent-bidders"), "server unique bidders independent of loaded history");
        TestSupport.equals("4", label(root, "room-extensions"), "server extension count");
        TestSupport.check(label(root, "room-price-growth").contains("50.000"), "absolute increase uses shared money format");
        TestSupport.check(label(root, "room-session-status").contains("d\u1eabn \u0111\u1ea7u"), "personal leading state");
        TestSupport.equals(4, chart(root).getData().get(0).getData().size(), "chart has opening price and three accepted bids");
        TestSupport.equals(110000.0, chart(root).getData().get(0).getData().get(1).getYValue().doubleValue(), "chart is in server acceptance order");
        TestSupport.check(label(root, "room-chart-note").contains("kh\u00f4ng ph\u1ea3i to\u00e0n b\u1ed9"), "history window is disclosed");
        VBox rows = (VBox) root.lookup("#room-bid-rows");
        TestSupport.equals(3, rows.getChildren().size(), "timeline shows real bids");
        TestSupport.check(rows.getChildren().get(0).getStyleClass().contains("leading"), "latest leader is highlighted");
        Object series = chart(root).getData().get(0);
        Object latest = rows.getChildren().get(0);
        room.update(leading.withTiming(leading.getEndTime(), "OPEN", 5), history, 2, true, START.plusSeconds(61));
        TestSupport.check(series == chart(root).getData().get(0), "watcher tick does not rebuild chart");
        TestSupport.check(latest == rows.getChildren().get(0), "watcher tick does not rebuild timeline or replay reveal");
        ToggleButton filter = (ToggleButton) root.lookup("#room-history-mine");
        filter.fire();
        TestSupport.equals(2, rows.getChildren().size(), "own-bid filter excludes other users");
        TestSupport.check(rows.getChildren().stream().allMatch(row -> row.getStyleClass().contains("own")), "filtered rows all belong to current user");
        filter.fire();
        TestSupport.equals(3, rows.getChildren().size(), "filter can restore all bids");
        room.history().lookupAll(".button").stream().filter(node -> node instanceof javafx.scene.control.Button)
                .map(node -> (javafx.scene.control.Button) node).forEach(javafx.scene.control.Button::fire);
        TestSupport.equals(1, historyOpens.get(), "open-history action is connected");
        snapshot(scene, "leading");

        ClientBid fourth = bid(4, 3, "minh", "180000", 40);
        List<ClientBid> contest = List.of(first, second, third, fourth);
        ClientAuction outbid = auction(1, "180000", 3L, "minh", "OPEN", START.plusSeconds(300));
        room.update(outbid, contest, 2, true, START.plusSeconds(280));
        TestSupport.check(label(root, "room-session-status").contains("v\u01b0\u1ee3t gi\u00e1"), "outbid state");
        TestSupport.check(root.lookup("#room-time-progress").getStyleClass().contains("urgent"), "last thirty seconds are highlighted");
        String unchangedNotice = label(root, "room-live-notice");
        ClientAuction extended = outbid.withTiming(START.plusSeconds(310), "OPEN", 4);
        room.update(extended, contest, 2, true, START.plusSeconds(281));
        TestSupport.equals(unchangedNotice, label(root, "room-live-notice"), "render/polling does not invent system events");
        room.tick(START.plusSeconds(311), true);
        TestSupport.equals(0.0, progress(root), "expired clock has no remaining progress");
        TestSupport.check(label(root, "room-session-status").contains("ch\u1ed1t phi\u00ean"), "clock alone cannot declare a winner");
        room.tick(START.plusSeconds(311), false);
        TestSupport.check(label(root, "room-session-status").contains("M\u1ea5t k\u1ebft n\u1ed1i"), "disconnected state warns about stale data");
        room.update(extended, contest, 1, true, START.plusSeconds(285));
        TestSupport.check(label(root, "room-session-status").contains("\u0111i\u1ec1u h\u00e0nh"), "host has a distinct role");
        room.update(auction(1, "180000", 3L, "minh", "ENDED", START.plusSeconds(310)), contest, 3, true, START.plusSeconds(311));
        TestSupport.check(label(root, "room-session-status").contains("\u0111\u00e3 th\u1eafng"), "only authoritative ended state declares winner");
        snapshot(scene, "finished");
        room.update(auction(1, "100000", null, "", "CANCELLED", START.plusSeconds(310)), List.of(), 3, true, START.plusSeconds(311));
        TestSupport.check(label(root, "room-session-status").contains("\u0111\u00e3 h\u1ee7y"), "cancelled room does not declare winner");
        room.update(auction(1, "100000", null, "", "ENDED", START.plusSeconds(310)), List.of(), 3, true, START.plusSeconds(311));
        TestSupport.check(label(root, "room-session-status").contains("Ch\u01b0a c\u00f3 ng\u01b0\u1eddi th\u1eafng"), "empty auction has no winner");
        room.clear();
        room.update(auction(2, "100000", null, "", "OPEN", START.plusSeconds(300)), contest, 2, true, START.plusSeconds(10));
        TestSupport.equals("...", label(root, "room-recent-bids"), "another room cannot inherit old statistics");
        TestSupport.equals(1, chart(root).getData().get(0).getData().size(), "chart resets on room switch");
        TestSupport.check(!filter.isSelected(), "filter resets on room switch");
        root.resize(850, 620);
        layout(root);
        TestSupport.check(room.insights().getBoundsInParent().getWidth() > 300, "compact room leaves chart usable");
        var historyBounds = room.history().localToParent(room.history().getLayoutBounds());
        var insightsBounds = room.insights().localToParent(room.insights().getLayoutBounds());
        TestSupport.check(historyBounds.getMinX() >= insightsBounds.getMaxX()
                && historyBounds.getMaxX() <= columns.getWidth() + 1, "compact column layouts do not overlap or overflow, excluding decorative shadows");
        snapshot(scene, "compact");
    }

    private static ClientAuction auction(long id, String price, Long winner, String username, String state, Instant end) {
        return new ClientAuction(id, id, "DEMO_" + id, "Studio Headphones", "Real auction data", 1, "studio",
                new BigDecimal("100000"), new BigDecimal("10000"), new BigDecimal(price), winner, username,
                START, end, state, "OPEN".equals(state) ? null : end, 5, 4);
    }

    private static ClientBid bid(long id, long user, String username, String amount, long seconds) {
        return new ClientBid(id, 1, user, username, new BigDecimal(amount), id, START.plusSeconds(seconds));
    }

    private static String label(BorderPane root, String id) { return ((Label) root.lookup("#" + id)).getText(); }
    private static double progress(BorderPane root) { return ((ProgressBar) root.lookup("#room-time-progress")).getProgress(); }
    @SuppressWarnings("unchecked")
    private static AreaChart<Number, Number> chart(BorderPane root) { return (AreaChart<Number, Number>) root.lookup("#room-price-chart"); }
    private static void layout(BorderPane root) { root.applyCss(); root.layout(); }

    private static void snapshot(Scene scene, String name) throws Exception {
        scene.getRoot().lookupAll(".room-status-title, .room-live-notice, .room-live-bid").forEach(node -> {
            node.setOpacity(1);
            node.setTranslateY(0);
        });
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        var image = scene.snapshot(null);
        BufferedImage bitmap = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < bitmap.getHeight(); row++) {
            for (int column = 0; column < bitmap.getWidth(); column++) {
                bitmap.setRGB(column, row, image.getPixelReader().getArgb(column, row));
            }
        }
        Path folder = Path.of("target", "live-room-preview");
        Files.createDirectories(folder);
        ImageIO.write(bitmap, "png", folder.resolve(name + ".png").toFile());
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result.get(30, TimeUnit.SECONDS);
    }
}
