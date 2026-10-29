package vn.ptit.btl16.client.fx;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import vn.ptit.btl16.common.fx.DesktopTheme;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

final class MarketplaceActivityPane extends VBox {
    private record Entry(String message, Instant time) { }
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private final VBox rows = new VBox(7);
    private final List<Label> ages = new ArrayList<>();

    MarketplaceActivityPane(Runnable showAll) {
        super(10); setId("marketplace-activity"); getStyleClass().add("market-activity");
        Button more = new Button("Xem tất cả  →"); more.getStyleClass().add("market-text-button");
        more.setOnAction(event -> showAll.run());
        HBox header = new HBox(8, DesktopTheme.label("Hoạt động gần đây", "market-section-title"), DesktopTheme.spacer(), more);
        header.setAlignment(Pos.CENTER_LEFT); getChildren().setAll(header, rows); render();
    }

    void add(String message) { entries.addFirst(new Entry(message, Instant.now())); while (entries.size() > 3) { entries.removeLast(); } render(); }
    void clear() { entries.clear(); render(); }
    void tick() {
        int index = 0;
        for (Entry entry : entries) { ages.get(index++).setText(relativeTime(entry.time())); }
    }
    private void render() {
        rows.getChildren().clear(); ages.clear();
        if (entries.isEmpty()) { rows.getChildren().add(DesktopTheme.label("Các cập nhật phiên đấu giá sẽ xuất hiện tại đây.", "market-activity-empty")); return; }
        for (Entry entry : entries) {
            Region dot = new Region(); dot.getStyleClass().add("market-activity-dot");
            Label message = new Label(entry.message()); message.getStyleClass().add("market-activity-message"); message.setMinWidth(0); message.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(message, Priority.ALWAYS);
            Label time = DesktopTheme.label(relativeTime(entry.time()), "market-activity-time"); time.setMinWidth(Region.USE_PREF_SIZE); ages.add(time);
            HBox row = new HBox(8, dot, MarketplaceIcons.icon("gavel", 12, "#8c652e"), message, time); row.setAlignment(Pos.CENTER_LEFT); rows.getChildren().add(row);
        }
    }
    private static String relativeTime(Instant time) { long minutes = Math.max(0, java.time.Duration.between(time, Instant.now()).toMinutes()); return minutes == 0 ? "Vừa xong" : minutes + " phút trước"; }
}
