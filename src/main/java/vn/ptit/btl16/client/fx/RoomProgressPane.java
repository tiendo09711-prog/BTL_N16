package vn.ptit.btl16.client.fx;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.model.ClientBid;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.fx.WalletUi;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.math.BigDecimal;

public final class RoomProgressPane extends VBox {
    private record Entry(long id, String text, String type, String actor, BigDecimal amount, Instant time) {
        @Override public String toString() { return text; }
    }
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private final ClientTransport transport;
    private final AuctionApi api;
    private final TreeMap<Long, Entry> entries = new TreeMap<>();
    private final ListView<Entry> list = new ListView<>();
    private final VBox flow = new VBox(10);
    private boolean flowLayout;
    private final Label status = new Label();
    private final Button older = new Button("Nh\u1eadt k\u00fd c\u0169 h\u01a1n");
    private final Button latest = new Button("Theo d\u00f5i m\u1edbi nh\u1ea5t");
    private Consumer<Map<String, String>> statistics = data -> { };
    private Consumer<Node> reveal = node -> { };
    private BigDecimal focusAmount;
    private long selectedEventId;
    private boolean pendingFocus;
    private int focusPages;
    private long room, generation, newestSeen, lastEvents, lastStats;
    private boolean loadingEvents, loadingStats, browsing, olderAvailable;

    public RoomProgressPane(ClientTransport transport) {
        this.transport = transport; api = new AuctionApi(transport); setSpacing(8); setMinSize(0, 0);
        list.setId("room-system-events"); status.setId("room-events-status"); status.setWrapText(true);
        list.setPlaceholder(new Label("Ch\u01b0a c\u00f3 s\u1ef1 ki\u1ec7n \u0111\u01b0\u1ee3c ghi nh\u1eadn"));
        list.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(Entry entry, boolean empty) {
                super.updateItem(entry, empty); setText(null);
                HBox card = empty || entry == null ? null : eventCard(entry);
                if (card != null) { card.prefWidthProperty().bind(view.widthProperty().subtract(25)); }
                setGraphic(card);
            }
        });
        list.setMinHeight(100); list.setPrefHeight(240); VBox.setVgrow(list, Priority.ALWAYS);
        older.setId("room-events-older"); latest.setId("room-events-latest");
        older.setOnAction(event -> { pendingFocus = false; if (!entries.isEmpty()) { events(entries.firstKey(), 0, true); } });
        latest.setOnAction(event -> { if (!loadingEvents) { pendingFocus = false; selectedEventId = 0; browsing = false; newestSeen = 0; events(Long.MAX_VALUE, 0, false); } });
        older.getStyleClass().add("room-journal-action"); latest.getStyleClass().add("room-journal-action");
        status.getStyleClass().add("muted");
        FlowPane actions = new FlowPane(8, 8, older, latest);
        getChildren().setAll(actions, status, list);
    }

    public void onStatistics(Consumer<Map<String, String>> consumer) { statistics = consumer; }
    public void onReveal(Consumer<Node> consumer) { reveal = consumer; }

    public void focusBid(ClientBid bid) {
        if (bid.getAuctionId() != room) { return; }
        focusAmount = bid.getAmount(); pendingFocus = true; focusPages = 0;
        continueFocus();
    }

    private void continueFocus() {
        if (!pendingFocus) { return; }
        Entry match = entries.values().stream().filter(entry -> "BID_ACCEPTED".equals(entry.type())
                && entry.amount() != null && entry.amount().compareTo(focusAmount) == 0).findFirst().orElse(null);
        if (match != null) {
            pendingFocus = false; selectedEventId = match.id(); browsing = true; renderEntries();
            status.setText("\u0110ang xem m\u1ed1c gi\u00e1 \u0111\u00e3 ch\u1ecdn");
            if (flowLayout) {
                Node row = flow.lookup("#room-event-" + match.id());
                if (row != null) { reveal.accept(row); }
            } else { list.scrollTo(new ArrayList<>(entries.keySet()).indexOf(match.id())); }
        } else if (!loadingEvents) {
            if (transport.isConnected() && focusPages == 0) {
                focusPages++;
                events(Long.MAX_VALUE, 0, false);
            } else if (transport.isConnected() && olderAvailable && !entries.isEmpty() && focusPages++ < 20) {
                events(entries.firstKey(), 0, true);
            } else {
                pendingFocus = false;
                status.setText("M\u1ed1c gi\u00e1 kh\u00f4ng n\u1eb1m trong nh\u1eadt k\u00fd \u0111\u00e3 t\u1ea3i.");
            }
        }
    }
    public void setFlowLayout(boolean value) {
        flowLayout = value;
        getChildren().set(2, value ? flow : list);
        renderEntries();
    }
    private void renderEntries() {
        list.getItems().setAll(entries.values());
        if (flowLayout) {
            flow.getChildren().clear();
            if (entries.isEmpty()) { flow.getChildren().add(new Label("Chưa có sự kiện được ghi nhận")); }
            for (Entry entry : entries.values()) {
                flow.getChildren().add(eventCard(entry));
            }
        }
    }
    private HBox eventCard(Entry entry) {
        boolean bid = "BID_ACCEPTED".equals(entry.type());
        String icon = switch (entry.type()) {
            case "BID_ACCEPTED" -> "gavel";
            case "JOINED", "LEFT" -> "users";
            case "EXTENDED_AUTO", "EXTENDED_MANUAL" -> "clock";
            case "ENDED" -> "trophy";
            default -> "info";
        };
        StackPane symbol = new StackPane(MarketplaceIcons.icon(icon, 16, bid ? "#80582f" : "#8d8173"));
        symbol.getStyleClass().add("room-event-icon");
        Label title = new Label(bid ? entry.actor() + " \u0111\u1eb7t gi\u00e1" : entry.text());
        title.setWrapText(true); title.setMinWidth(0); title.getStyleClass().add("room-event-title");
        VBox body = new VBox(6, title); body.setMinWidth(0); HBox.setHgrow(body, Priority.ALWAYS);
        if (bid && entry.amount() != null) {
            Label amount = new Label(WalletUi.money(entry.amount())); amount.getStyleClass().add("room-event-amount");
            amount.setWrapText(true); body.getChildren().add(amount);
        }
        Label clock = new Label(DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(entry.time()));
        clock.getStyleClass().add("room-event-time"); clock.setMinWidth(Region.USE_PREF_SIZE);
        Tooltip.install(clock, new Tooltip(TIME.format(entry.time())));
        HBox card = new HBox(12, symbol, body, clock); card.setAlignment(Pos.TOP_LEFT);
        card.setId("room-event-" + entry.id()); card.getStyleClass().add("room-event-row");
        if (bid) { card.getStyleClass().add("bid-event"); }
        if (entry.id() == selectedEventId) { card.getStyleClass().add("selected-event"); }
        card.setAccessibleText(TIME.format(entry.time()) + " " + entry.text());
        return card;
    }
    public void setRoom(long value) {
        if (room != value) { clear(); room = value; statistics.accept(Map.of("state", "LOADING")); }
        tick();
    }
    public void invalidateRequests() { generation++; loadingEvents = false; loadingStats = false; lastEvents = 0; lastStats = 0; }
    public void clear() { invalidateRequests(); room = 0; newestSeen = 0; browsing = false; olderAvailable = false;
        pendingFocus = false; selectedEventId = 0; focusAmount = null; entries.clear(); renderEntries(); status.setText(""); }

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
                pendingFocus = false;
                status.setText("Kh\u00f4ng t\u1ea3i \u0111\u01b0\u1ee3c nh\u1eadt k\u00fd: " + (response == null ? "l\u1ed7i k\u1ebft n\u1ed1i" : response.getMessageText()));
                older.setDisable(entries.isEmpty()); return;
            }
            var data = response.getWireMessage().getData();
            if (olderPage || after == 0) { entries.clear(); }
            browsing = olderPage;
            for (int index = 0; index < Math.min(50, ClientWireParser.integer(data, "eventCount", 0)); index++) {
                String prefix = "event." + index + "."; long id = ClientWireParser.longValue(data, prefix + "id", 0);
                if (id > 0) {
                    entries.put(id, new Entry(id, describe(data, prefix), data.getOrDefault(prefix + "type", ""),
                            data.getOrDefault(prefix + "username", ""), data.getOrDefault(prefix + "amount", "").isBlank()
                            ? null : ClientWireParser.decimal(data, prefix + "amount"), ClientWireParser.instant(data, prefix + "time")));
                    if (!olderPage) { newestSeen = Math.max(newestSeen, id); }
                }
            }
            if (after == 0) { olderAvailable = Boolean.parseBoolean(response.get("hasMore")) && (entries.isEmpty() || entries.firstKey() < before); }
            while (entries.size() > 200) { entries.pollFirstEntry(); olderAvailable = true; }
            renderEntries();
            boolean more = Boolean.parseBoolean(response.get("hasMore"));
            older.setDisable(entries.isEmpty() || !olderAvailable);
            if (after > 0 && more) { lastEvents = 0; }
            status.setText(browsing ? "Nh\u1eadt k\u00fd c\u0169" : entries.size() + " s\u1ef1 ki\u1ec7n");
            continueFocus();
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
        return text;
    }
}
