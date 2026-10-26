package vn.ptit.btl16.client.fx;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.fx.WalletUi;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

public final class RoomProgressPane extends VBox {
    private record Entry(long id, String text) { @Override public String toString() { return text; } }
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private final ClientTransport transport;
    private final AuctionApi api;
    private final TreeMap<Long, Entry> entries = new TreeMap<>();
    private final ListView<Entry> list = new ListView<>();
    private final Label status = new Label();
    private final Button older = new Button("Nh\u1eadt k\u00fd c\u0169 h\u01a1n");
    private final Button latest = new Button("Theo d\u00f5i m\u1edbi nh\u1ea5t");
    private Consumer<Map<String, String>> statistics = data -> { };
    private long room, generation, newestSeen, lastEvents, lastStats;
    private boolean loadingEvents, loadingStats, browsing, olderAvailable;

    public RoomProgressPane(ClientTransport transport) {
        this.transport = transport; api = new AuctionApi(transport); setSpacing(8); setMinHeight(0);
        list.setId("room-system-events"); status.setId("room-events-status"); status.setWrapText(true);
        list.setPlaceholder(new Label("Ch\u01b0a c\u00f3 s\u1ef1 ki\u1ec7n \u0111\u01b0\u1ee3c ghi nh\u1eadn"));
        list.setCellFactory(view -> new ListCell<>() {
            private final Label text = new Label();
            { text.setWrapText(true); text.maxWidthProperty().bind(view.widthProperty().subtract(35)); }
            @Override protected void updateItem(Entry entry, boolean empty) {
                super.updateItem(entry, empty); setText(null); text.setText(empty || entry == null ? "" : entry.text()); setGraphic(empty ? null : text);
            }
        });
        list.setMinHeight(100); list.setPrefHeight(240); VBox.setVgrow(list, Priority.ALWAYS);
        older.setId("room-events-older"); latest.setId("room-events-latest");
        older.setOnAction(event -> { if (!entries.isEmpty()) { events(entries.firstKey(), 0, true); } });
        latest.setOnAction(event -> { if (!loadingEvents) { browsing = false; newestSeen = 0; events(Long.MAX_VALUE, 0, false); } });
        getChildren().setAll(new HBox(8, older, latest), status, list);
    }

    public void onStatistics(Consumer<Map<String, String>> consumer) { statistics = consumer; }
    public void setRoom(long value) {
        if (room != value) { clear(); room = value; statistics.accept(Map.of("state", "LOADING")); }
        tick();
    }
    public void invalidateRequests() { generation++; loadingEvents = false; loadingStats = false; lastEvents = 0; lastStats = 0; }
    public void clear() { invalidateRequests(); room = 0; newestSeen = 0; browsing = false; olderAvailable = false; entries.clear(); list.getItems().clear(); status.setText(""); }

    public void tick() {
        if (room == 0) { return; }
        if (!transport.isConnected()) {
            statistics.accept(Map.of("state", "ERROR"));
            status.setText("M\u1ea5t k\u1ebft n\u1ed1i \u00b7 Nh\u1eadt k\u00fd gi\u1eef b\u1ea3n \u0111\u00e3 t\u1ea3i"); older.setDisable(true); latest.setDisable(true); return;
        }
        latest.setDisable(loadingEvents);
        long now = System.nanoTime();
        if (!browsing && !loadingEvents && now - lastEvents > 3_000_000_000L) { events(Long.MAX_VALUE, newestSeen, false); }
        if (!loadingStats && now - lastStats > 3_000_000_000L) {
            loadingStats = true; lastStats = now; long token = generation;
            api.stats(room).whenComplete((response, error) -> Platform.runLater(() -> {
                if (token != generation || room == 0) { return; }
                loadingStats = false;
                if (!transport.isConnected() || error != null || response == null || !response.isSuccess()) { statistics.accept(Map.of("state", "ERROR")); return; }
                statistics.accept(response.getWireMessage().getData());
            }));
        }
    }

    private void events(long before, long after, boolean olderPage) {
        if (room == 0 || loadingEvents || !transport.isConnected()) { return; }
        loadingEvents = true; lastEvents = System.nanoTime(); long token = generation;
        older.setDisable(true); latest.setDisable(true); status.setText("\u0110ang t\u1ea3i nh\u1eadt k\u00fd t\u1eeb server...");
        api.events(room, before, after).whenComplete((response, error) -> Platform.runLater(() -> {
            if (token != generation || room == 0) { return; }
            loadingEvents = false; latest.setDisable(false);
            if (!transport.isConnected() || error != null || response == null || !response.isSuccess()) {
                status.setText("Kh\u00f4ng t\u1ea3i \u0111\u01b0\u1ee3c nh\u1eadt k\u00fd: " + (response == null ? "l\u1ed7i k\u1ebft n\u1ed1i" : response.getMessageText()));
                older.setDisable(entries.isEmpty()); return;
            }
            var data = response.getWireMessage().getData();
            if (olderPage || after == 0) { entries.clear(); }
            browsing = olderPage;
            for (int index = 0; index < Math.min(50, ClientWireParser.integer(data, "eventCount", 0)); index++) {
                String prefix = "event." + index + "."; long id = ClientWireParser.longValue(data, prefix + "id", 0);
                if (id > 0) { entries.put(id, new Entry(id, describe(data, prefix))); if (!olderPage) { newestSeen = Math.max(newestSeen, id); } }
            }
            if (after == 0) { olderAvailable = Boolean.parseBoolean(response.get("hasMore")); }
            while (entries.size() > 200) { entries.pollFirstEntry(); olderAvailable = true; }
            list.getItems().setAll(entries.values());
            boolean more = Boolean.parseBoolean(response.get("hasMore"));
            older.setDisable(entries.isEmpty() || !olderAvailable);
            if (after > 0 && more) { lastEvents = 0; }
            status.setText(browsing ? "\u0110ang xem trang c\u0169 \u00b7 B\u1ea5m Theo d\u00f5i m\u1edbi nh\u1ea5t \u0111\u1ec3 ti\u1ebfp t\u1ee5c."
                    : entries.size() + " s\u1ef1 ki\u1ec7n \u0111\u00e3 t\u1ea3i (t\u1ed1i \u0111a 200) \u00b7 Th\u1eddi gian server");
        }));
    }

    private static String describe(Map<String, String> data, String prefix) {
        String actor = data.getOrDefault(prefix + "username", "");
        String amount = data.getOrDefault(prefix + "amount", "").isBlank() ? "" : WalletUi.money(ClientWireParser.decimal(data, prefix + "amount"));
        String text = switch (data.getOrDefault(prefix + "type", "")) {
            case "CREATED" -> "T\u1ea1o phi\u00ean";
            case "JOINED" -> actor + " tham gia ph\u00f2ng";
            case "LEFT" -> actor + " r\u1eddi ph\u00f2ng";
            case "BID_ACCEPTED" -> "Ch\u1ea5p nh\u1eadn gi\u00e1 " + amount + " c\u1ee7a " + actor;
            case "LEADER_CHANGED" -> actor + " tr\u1edf th\u00e0nh ng\u01b0\u1eddi d\u1eabn \u0111\u1ea7u";
            case "EXTENDED_MANUAL", "EXTENDED_AUTO" -> (data.get(prefix + "type").equals("EXTENDED_AUTO") ? "T\u1ef1 \u0111\u1ed9ng gia h\u1ea1n" : "Ch\u1ee7 ph\u00f2ng gia h\u1ea1n")
                    + " \u00b7 H\u1ea1n m\u1edbi: " + TIME.format(Instant.parse(data.get(prefix + "detail")));
            case "ENDED" -> "K\u1ebft th\u00fac phi\u00ean" + (actor.isEmpty() ? " \u00b7 Kh\u00f4ng c\u00f3 ng\u01b0\u1eddi th\u1eafng" : " \u00b7 " + actor + " \u00b7 " + amount);
            case "CANCELLED" -> "Ch\u1ee7 ph\u00f2ng h\u1ee7y phi\u00ean";
            default -> "S\u1ef1 ki\u1ec7n h\u1ec7 th\u1ed1ng";
        };
        return TIME.format(ClientWireParser.instant(data, prefix + "time")) + "  " + text;
    }
}
