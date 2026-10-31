package vn.ptit.btl16.client.fx;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.fx.WalletUi;
import vn.ptit.btl16.common.fx.DesktopTheme;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BooleanSupplier;

public final class BidHistoryPane extends VBox {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS\ndd/MM/yyyy").withZone(ZoneId.systemDefault());
    private final AuctionApi api;
    private final ClientTransport transport;
    private final long room;
    private final BooleanSupplier valid;
    private final TableView<Map<String, String>> table = new TableView<>();
    private final CheckBox mine = new CheckBox("L\u01b0\u1ee3t c\u1ee7a t\u00f4i");
    private final Button older = new Button("Trang c\u0169 h\u01a1n");
    private final Button newest = new Button("M\u1edbi nh\u1ea5t");
    private final Label note = new Label();
    private final Label pageSummary = DesktopTheme.label("", "room-history-page");
    private final VBox compactRows = new VBox(10);
    private final ScrollPane compact = new ScrollPane(compactRows);
    private int page;
    private Long through;
    private long before = Long.MAX_VALUE, generation;
    private boolean loading, disposed;

    public BidHistoryPane(ClientTransport transport, long room, BooleanSupplier valid) {
        this.transport = transport; this.api = new AuctionApi(transport); this.room = room; this.valid = valid;
        setSpacing(16); setMinSize(0, 200); setPrefSize(800, 470); setId("bid-history-pane");
        getStyleClass().add("room-full-history");
        table.setId("bid-history-table"); table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setMinSize(0, 120); table.setFixedCellSize(66);
        column("Th\u1eddi gian", "time"); column("Ng\u01b0\u1eddi \u0111\u1eb7t", "name");
        column("S\u1ed1 ti\u1ec1n", "amount"); column("M\u1ee9c t\u0103ng", "increase");
        table.setPlaceholder(emptyState());
        note.setWrapText(true); note.setMinHeight(Region.USE_PREF_SIZE);
        note.setId("bid-history-status"); mine.setId("bid-history-mine"); older.setId("bid-history-older");
        newest.setId("bid-history-newest"); newest.getStyleClass().add("ghost"); older.getStyleClass().add("ghost");
        mine.setOnAction(event -> reset()); newest.setOnAction(event -> reset()); older.setOnAction(event -> load());
        Label title = DesktopTheme.label("L\u1ecbch s\u1eed \u0111\u1eb7t gi\u00e1", "room-dialog-title");
        Label subtitle = DesktopTheme.label("PH\u00d2NG #" + room + "  /  D\u1eee LI\u1ec6U T\u1eea SERVER", "eyebrow");
        FlowPane tools = new FlowPane(12, 10, mine, newest, older); tools.setAlignment(Pos.CENTER_LEFT);
        tools.setMinHeight(Region.USE_PREF_SIZE); pageSummary.setMinHeight(Region.USE_PREF_SIZE);
        tools.getStyleClass().add("room-history-tools");
        note.getStyleClass().add("muted");
        compact.setFitToWidth(true); compact.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); compact.setMinHeight(120);
        compact.getStyleClass().add("social-scroll");
        StackPane results = new StackPane(table, compact); results.setMinSize(0, 120); VBox.setVgrow(results, Priority.ALWAYS);
        widthProperty().addListener((observable, before, after) -> adapt()); adapt();
        getChildren().setAll(new VBox(5, subtitle, title), tools, pageSummary, results, note);
        load();
    }

    private void column(String name, String key) {
        TableColumn<Map<String, String>, String> column = new TableColumn<>(name);
        column.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().get(key)));
        column.setPrefWidth("amount".equals(key) ? 215 : "time".equals(key) ? 175 : 190); column.setMinWidth(70);
        column.setCellFactory(view -> new TableCell<>() {
            private final Label value = new Label();
            { value.setWrapText(true); value.getStyleClass().add("amount".equals(key) ? "room-history-money" : "increase".equals(key) ? "room-history-increase" : "room-history-cell"); }
            @Override protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty); setText(null); setGraphic(null);
                if (!empty && text != null) {
                    value.setText(text); value.setMaxWidth(Double.MAX_VALUE);
                    value.prefWidthProperty().bind(widthProperty().subtract(20)); setGraphic(value); setTooltip(new Tooltip(text));
                    setAlignment("amount".equals(key) || "increase".equals(key) ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                } else { setTooltip(null); }
            }
        });
        column.setSortable(false); table.getColumns().add(column);
    }

    private void adapt() {
        boolean narrow = getWidth() > 0 && getWidth() < 620;
        compact.setVisible(narrow); compact.setManaged(narrow); table.setVisible(!narrow); table.setManaged(!narrow);
    }

    private VBox emptyState() {
        VBox empty = new VBox(8, DesktopTheme.label("Ch\u01b0a c\u00f3 l\u01b0\u1ee3t \u0111\u1eb7t gi\u00e1", "section-title"),
                DesktopTheme.label(mine.isSelected() ? "B\u1ea1n ch\u01b0a c\u00f3 l\u01b0\u1ee3t h\u1ee3p l\u1ec7 trong ph\u1ea7n l\u1ecbch s\u1eed n\u00e0y." : "C\u00e1c l\u01b0\u1ee3t \u0111\u01b0\u1ee3c máy chủ ch\u1ea5p nh\u1eadn s\u1ebd xu\u1ea5t hi\u1ec7n t\u1ea1i \u0111\u00e2y.", "muted"));
        empty.setAlignment(Pos.CENTER); empty.getStyleClass().add("room-empty-state"); return empty;
    }

    private void renderCompact() {
        compactRows.getChildren().clear(); table.setPlaceholder(emptyState());
        if (table.getItems().isEmpty()) { compactRows.getChildren().add(emptyState()); }
        for (Map<String, String> row : table.getItems()) {
            Label name = DesktopTheme.label(row.get("name"), "social-name");
            Label amount = DesktopTheme.label(row.get("amount"), "room-history-money");
            VBox card = new VBox(7, name, amount, DesktopTheme.label(row.get("time").replace('\n', ' ') + " (máy chủ)", "muted"),
                    DesktopTheme.label("T\u0103ng " + row.get("increase"), "room-history-increase"));
            card.getStyleClass().add("room-history-entry"); compactRows.getChildren().add(card);
        }
        compact.setVvalue(0);
    }

    private void reset() { generation++; loading = false; page = 0; through = null; before = Long.MAX_VALUE; table.getItems().clear(); compactRows.getChildren().clear(); load(); }
    public void dispose() { disposed = true; generation++; table.getItems().clear(); compactRows.getChildren().clear(); }

    private void load() {
        if (disposed || loading) { return; }
        if (!valid.getAsBoolean() || !transport.isConnected()) {
            note.setText("M\u1ea5t k\u1ebft n\u1ed1i ho\u1eb7c phi\u00ean \u0111\u00e3 thay \u0111\u1ed5i. M\u1edf l\u1ea1i l\u1ecbch s\u1eed sau khi \u0111\u1ed3ng b\u1ed9."); older.setDisable(true); return;
        }
        loading = true; older.setDisable(true); newest.setDisable(true); mine.setDisable(true);
        note.setText("\u0110ang t\u1ea3i t\u1eeb máy chủ..."); long token = generation;
        api.history(room, before, through, mine.isSelected()).whenComplete((response, error) -> Platform.runLater(() -> {
            if (disposed || token != generation) { return; }
            loading = false; newest.setDisable(false); mine.setDisable(false);
            if (!valid.getAsBoolean() || !transport.isConnected()) { note.setText("Phi\u00ean \u0111\u00e3 thay \u0111\u1ed5i; b\u1ecf qua d\u1eef li\u1ec7u c\u0169."); return; }
            if (error != null || response == null || !response.isSuccess()) {
                note.setText("Kh\u00f4ng t\u1ea3i \u0111\u01b0\u1ee3c: " + (response == null ? "l\u1ed7i k\u1ebft n\u1ed1i" : response.getUserMessage()) + ". B\u1ea5m \u0111\u1ec3 th\u1eed l\u1ea1i."); older.setDisable(false); return;
            }
            var data = response.getWireMessage().getData();
            through = ClientWireParser.longValue(data, "throughSequence", 0);
            before = ClientWireParser.longValue(data, "nextBeforeSequence", 0);
            List<Map<String, String>> rows = new ArrayList<>();
            for (int index = 0; index < Math.min(50, ClientWireParser.integer(data, "bidCount", 0)); index++) {
                String prefix = "bid." + index + ".";
                rows.add(Map.of("time", TIME.format(ClientWireParser.instant(data, prefix + "createdAt")),
                        "name", data.getOrDefault(prefix + "username", ""),
                        "amount", WalletUi.money(ClientWireParser.decimal(data, prefix + "amount")),
                        "increase", "+" + WalletUi.money(ClientWireParser.decimal(data, prefix + "increase"))));
            }
            table.getItems().setAll(rows); renderCompact(); page++;
            older.setDisable(!Boolean.parseBoolean(response.get("hasMore")));
            pageSummary.setText("Trang " + page + " \u00b7 " + rows.size() + " l\u01b0\u1ee3t" + (mine.isSelected() ? " c\u1ee7a b\u1ea1n" : " \u0111\u1eb7t gi\u00e1"));
            note.setText("M\u1edbi nh\u1ea5t tr\u01b0\u1edbc \u00b7 Th\u1eddi gian máy chủ. S\u1ed1 l\u01b0\u1ee3t ch\u1ec9 t\u00ednh trong trang; b\u1ea5m M\u1edbi nh\u1ea5t \u0111\u1ec3 c\u1eadp nh\u1eadt.");
        }));
    }
}
