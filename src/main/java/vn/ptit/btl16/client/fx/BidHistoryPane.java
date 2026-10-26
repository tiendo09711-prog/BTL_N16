package vn.ptit.btl16.client.fx;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.fx.WalletUi;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BooleanSupplier;

public final class BidHistoryPane extends VBox {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private final AuctionApi api;
    private final ClientTransport transport;
    private final long room;
    private final BooleanSupplier valid;
    private final TableView<Map<String, String>> table = new TableView<>();
    private final CheckBox mine = new CheckBox("L\u01b0\u1ee3t c\u1ee7a t\u00f4i");
    private final Button older = new Button("Trang c\u0169 h\u01a1n");
    private final Button newest = new Button("M\u1edbi nh\u1ea5t");
    private final Label note = new Label();
    private Long through;
    private long before = Long.MAX_VALUE, generation;
    private boolean loading, disposed;

    public BidHistoryPane(ClientTransport transport, long room, BooleanSupplier valid) {
        this.transport = transport; this.api = new AuctionApi(transport); this.room = room; this.valid = valid;
        setSpacing(10); setMinHeight(180); setPrefSize(680, 380); setId("bid-history-pane");
        table.setId("bid-history-table"); table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        column("Th\u1eddi gian (server)", "time"); column("Ng\u01b0\u1eddi \u0111\u1eb7t", "name");
        column("S\u1ed1 ti\u1ec1n", "amount"); column("M\u1ee9c t\u0103ng", "increase");
        table.setPlaceholder(new Label("Ch\u01b0a c\u00f3 l\u01b0\u1ee3t h\u1ee3p l\u1ec7"));
        note.setWrapText(true); note.setId("bid-history-status"); mine.setId("bid-history-mine"); older.setId("bid-history-older");
        mine.setOnAction(event -> reset()); newest.setOnAction(event -> reset()); older.setOnAction(event -> load());
        getChildren().setAll(new HBox(10, mine, newest, older), note, table); VBox.setVgrow(table, Priority.ALWAYS);
        load();
    }

    private void column(String name, String key) {
        TableColumn<Map<String, String>, String> column = new TableColumn<>(name);
        column.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().get(key)));
        column.setSortable(false); table.getColumns().add(column);
    }

    private void reset() { generation++; loading = false; through = null; before = Long.MAX_VALUE; table.getItems().clear(); load(); }
    public void dispose() { disposed = true; generation++; table.getItems().clear(); }

    private void load() {
        if (disposed || loading) { return; }
        if (!valid.getAsBoolean() || !transport.isConnected()) {
            note.setText("M\u1ea5t k\u1ebft n\u1ed1i ho\u1eb7c phi\u00ean \u0111\u00e3 thay \u0111\u1ed5i. M\u1edf l\u1ea1i l\u1ecbch s\u1eed sau khi \u0111\u1ed3ng b\u1ed9."); older.setDisable(true); return;
        }
        loading = true; older.setDisable(true); newest.setDisable(true); mine.setDisable(true);
        note.setText("\u0110ang t\u1ea3i t\u1eeb server..."); long token = generation;
        api.history(room, before, through, mine.isSelected()).whenComplete((response, error) -> Platform.runLater(() -> {
            if (disposed || token != generation) { return; }
            loading = false; newest.setDisable(false); mine.setDisable(false);
            if (!valid.getAsBoolean() || !transport.isConnected()) { note.setText("Phi\u00ean \u0111\u00e3 thay \u0111\u1ed5i; b\u1ecf qua d\u1eef li\u1ec7u c\u0169."); return; }
            if (error != null || response == null || !response.isSuccess()) {
                note.setText("Kh\u00f4ng t\u1ea3i \u0111\u01b0\u1ee3c: " + (response == null ? "l\u1ed7i k\u1ebft n\u1ed1i" : response.getMessageText()) + ". B\u1ea5m \u0111\u1ec3 th\u1eed l\u1ea1i."); older.setDisable(false); return;
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
            table.getItems().setAll(rows); older.setDisable(!Boolean.parseBoolean(response.get("hasMore")));
            note.setText(rows.size() + " l\u01b0\u1ee3t trong trang n\u00e0y \u00b7 Kh\u00f4ng ph\u1ea3i t\u1ed5ng to\u00e0n phi\u00ean \u00b7 M\u1edbi nh\u1ea5t \u0111\u1ec3 c\u1eadp nh\u1eadt.");
        }));
    }
}
