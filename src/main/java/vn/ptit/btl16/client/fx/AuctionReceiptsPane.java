package vn.ptit.btl16.client.fx;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.fx.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.BooleanSupplier;

public final class AuctionReceiptsPane extends BorderPane {
    private final AuctionReceiptApi api;
    private final ClientTransport transport;
    private final BooleanSupplier sameSession;
    private final Runnable openWallet;
    private final Stage stage = new Stage();
    private final ComboBox<String> scope = new ComboBox<>(), outcome = new ComboBox<>();
    private final TextField search = new TextField();
    private final TableView<Map<String, String>> history = new TableView<>();
    private final VBox detail = new VBox(12);
    private final Label message = new Label();
    private final Button next = new Button("Trang ti\u1ebfp"), refresh = new Button("L\u00e0m m\u1edbi");
    private final Timeline sessionGuard;
    private long listGeneration, receiptGeneration, before = Long.MAX_VALUE, nextBefore, selectedRoom;
    private boolean closed;

    public AuctionReceiptsPane(Window owner, ClientTransport transport, BooleanSupplier sameSession, Runnable openWallet) {
        this.api = new AuctionReceiptApi(transport); this.transport = transport; this.sameSession = sameSession; this.openWallet = openWallet;
        stage.initOwner(owner); stage.setTitle("K\u1ebft qu\u1ea3 phi\u00ean c\u1ee7a t\u00f4i");
        scope.getItems().setAll("PARTICIPATED", "WON", "HOSTED"); scope.setValue("PARTICIPATED");
        outcome.getItems().setAll("ALL", "WON", "NO_BIDS", "CANCELLED"); outcome.setValue("ALL");
        scope.setConverter(converter(true)); outcome.setConverter(converter(false));
        scope.setId("receipt-scope"); outcome.setId("receipt-outcome"); search.setId("receipt-search");
        search.setPromptText("T\u00ean s\u1ea3n ph\u1ea9m / m\u00e3 phi\u00ean"); HBox.setHgrow(search, Priority.ALWAYS);
        Button first = new Button("Trang \u0111\u1ea7u"); next.setDisable(true); next.setId("receipts-next"); refresh.setId("receipts-refresh");
        FlowPane filters = new FlowPane(8, 8, scope, outcome, search, refresh, first, next);
        VBox heading = new VBox(10, DesktopTheme.label("K\u1ebft qu\u1ea3 & bi\u00ean nh\u1eadn c\u00e1 nh\u00e2n", "page-title"),
                DesktopTheme.label("Ch\u1ec9 k\u1ebft qu\u1ea3 server \u0111\u00e3 ch\u1ed1t. V\u00ed m\u00f4 ph\u1ecfng, kh\u00f4ng chuy\u1ec3n ti\u1ec1n th\u1eadt.", "muted"), filters, message);
        heading.setPadding(new Insets(18)); setTop(heading);
        history.setId("receipts-history"); history.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        WalletUi.column(history, "PHI\u00caN / S\u1ea2N PH\u1ea8M", row -> "#" + row.get("auctionId") + " \u00b7 " + row.get("productName"), 280);
        WalletUi.column(history, "K\u1ebeT QU\u1ea2", row -> outcomeText(row.get("outcome")), 190);
        WalletUi.column(history, "C\u1ee6A B\u1ea0N", row -> roleText(row.get("personalRole")), 150);
        WalletUi.column(history, "K\u1ebeT TH\u00daC", row -> time(row.get("endedAt")), 160);
        history.setPlaceholder(new Label("Ch\u01b0a c\u00f3 bi\u00ean nh\u1eadn \u0111\u00e3 ch\u1ed1t theo b\u1ed9 l\u1ecdc."));
        history.getSelectionModel().selectedItemProperty().addListener((observable, previous, value) -> {
            if (value != null) { loadReceipt(Long.parseLong(value.get("auctionId"))); }
        });
        ScrollPane detailScroll = new ScrollPane(detail); detailScroll.setFitToWidth(true); detail.setPadding(new Insets(18)); detail.setId("receipt-detail");
        SplitPane split = new SplitPane(history, detailScroll); split.setOrientation(javafx.geometry.Orientation.VERTICAL); split.setDividerPositions(0.42); setCenter(split);
        refresh.setOnAction(event -> { reload(); if (selectedRoom > 0) { loadReceipt(selectedRoom); } });
        first.setOnAction(event -> { before = Long.MAX_VALUE; reload(); });
        next.setOnAction(event -> { before = nextBefore; reload(); });
        scope.setOnAction(event -> reset()); outcome.setOnAction(event -> reset()); search.setOnAction(event -> reset());
        sessionGuard = new Timeline(new KeyFrame(Duration.seconds(1), event -> { if (!sameSession.getAsBoolean()) { stage.close(); } }));
        sessionGuard.setCycleCount(Timeline.INDEFINITE);
        stage.setOnHidden(event -> { closed = true; listGeneration++; receiptGeneration++; sessionGuard.stop(); history.getItems().clear(); detail.getChildren().clear(); });
        Scene scene = new Scene(this, 940, 740); DesktopTheme.apply(scene); stage.setScene(scene); stage.setMinWidth(620); stage.setMinHeight(480);
    }
    public void show(long room) {
        stage.show(); sessionGuard.play(); reload(); if (room > 0) { loadReceipt(room); }
    }
    private boolean active() { return !closed && sameSession.getAsBoolean(); }
    private void reset() { before = Long.MAX_VALUE; reload(); }
    private void reload() {
        long generation = ++listGeneration; next.setDisable(true);
        if (!active()) { return; }
        history.getItems().clear(); message.setText("\u0110ang t\u1ea3i l\u1ecbch s\u1eed t\u1eeb server...");
        if (!transport.isConnected()) { message.setText("M\u1ea5t k\u1ebft n\u1ed1i. K\u1ebft n\u1ed1i l\u1ea1i v\u00e0 b\u1ea5m L\u00e0m m\u1edbi; ch\u01b0a x\u00e1c nh\u1eadn k\u1ebft qu\u1ea3."); return; }
        api.list(scope.getValue(), outcome.getValue(), search.getText().trim(), before).whenComplete((response, error) -> Platform.runLater(() -> {
            if (!active() || generation != listGeneration) { return; }
            if (error != null || !response.isSuccess()) { message.setText(error != null ? "Ch\u01b0a t\u1ea3i \u0111\u01b0\u1ee3c. B\u1ea5m L\u00e0m m\u1edbi \u0111\u1ec3 th\u1eed l\u1ea1i." : response.getMessageText()); return; }
            var data = response.getWireMessage().getData(); int count = number(data, "count");
            List<Map<String, String>> rows = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                String prefix = "receipt." + index + "."; Map<String, String> row = new LinkedHashMap<>();
                data.forEach((key, value) -> { if (key.startsWith(prefix)) { row.put(key.substring(prefix.length()), value); } }); rows.add(row);
            }
            history.getItems().setAll(rows); nextBefore = Long.parseLong(data.getOrDefault("nextBeforeId", "0")); next.setDisable(!"true".equals(data.get("hasMore")));
            message.setText(count + " bi\u00ean nh\u1eadn. Ch\u1ecdn m\u1ed9t phi\u00ean \u0111\u1ec3 xem l\u1ea1i k\u1ebft qu\u1ea3 c\u1ee7a b\u1ea1n.");
        }));
    }
    public void loadReceipt(long room) {
        if (!active()) { return; }
        selectedRoom = room; long generation = ++receiptGeneration;
        detail.getChildren().setAll(new Label("\u0110ang ch\u1edd bi\u00ean nh\u1eadn server x\u00e1c nh\u1eadn cho #" + room));
        Button retry = new Button("Th\u1eed l\u1ea1i bi\u00ean nh\u1eadn"); retry.setOnAction(event -> loadReceipt(room)); detail.getChildren().add(retry);
        if (!transport.isConnected()) { detail.getChildren().add(new Label("M\u1ea5t k\u1ebft n\u1ed1i; kh\u00f4ng suy ra th\u1eafng hay thanh to\u00e1n t\u1eeb d\u1eef li\u1ec7u c\u0169.")); return; }
        api.get(room).whenComplete((response, error) -> Platform.runLater(() -> {
            if (!active() || generation != receiptGeneration) { return; }
            if (error != null || !response.isSuccess()) {
                detail.getChildren().setAll(new Label(error != null ? "Ch\u01b0a x\u00e1c nh\u1eadn \u0111\u01b0\u1ee3c k\u1ebft qu\u1ea3. H\u00e3y th\u1eed l\u1ea1i." : response.getMessageText()), retry); return;
            }
            render(response.getWireMessage().getData());
        }));
    }
    private void render(Map<String, String> data) {
        Label title = DesktopTheme.label("#" + data.get("auctionId") + " \u00b7 " + data.get("productName"), "section-title"); title.setWrapText(true);
        Label personal = DesktopTheme.label(roleText(data.get("personalRole")), "stat-value"); personal.setId("receipt-personal-result");
        Label result = new Label(outcomeText(data.get("outcome")) + " \u00b7 " + time(data.get("endedAt")));
        Label winner = new Label("Ng\u01b0\u1eddi th\u1eafng: " + (data.getOrDefault("winnerDisplay", "").isBlank() ? "Kh\u00f4ng c\u00f3" : data.get("winnerDisplay"))); winner.setId("receipt-winner");
        Label price = new Label("Gi\u00e1 ch\u1ed1t: " + money(data.get("finalPrice")));
        Label stats = new Label("T\u1ed5ng bid: " + data.get("totalBids") + "   |   Ng\u01b0\u1eddi bid: " + data.get("uniqueBidders") + "   |   Gia h\u1ea1n: " + data.get("extensionCount")
                + ("true".equals(data.get("extensionsComplete")) ? "" : " (nh\u1eadt k\u00fd c\u0169 ch\u01b0a \u0111\u1ee7)")); stats.setWrapText(true);
        Label wallet = new Label("V\u00ed m\u00f4 ph\u1ecfng: " + walletText(data.get("walletStatus")) + " \u00b7 C\u00f2n gi\u1eef: " + money(data.get("heldAmount"))); wallet.setId("receipt-wallet"); wallet.setWrapText(true);
        Button ledger = new Button("Xem giao d\u1ecbch v\u00ed c\u1ee7a t\u00f4i trong phi\u00ean"); ledger.setId("receipt-ledger-link");
        VBox transactions = new VBox(8); transactions.setVisible(false); transactions.setManaged(false); transactions.setId("receipt-ledger");
        TableView<Map<String, String>> entries = new TableView<>(); entries.setPrefHeight(220);
        WalletUi.column(entries, "GIAO D\u1ecaCH", row -> WalletUi.type(row.get("type")), 190);
        WalletUi.column(entries, "TI\u1ec0N +/-", row -> money(row.get("amount")), 120);
        WalletUi.column(entries, "GI\u1eee +/-", row -> money(row.get("reservedDelta")), 120);
        WalletUi.column(entries, "THAM CHI\u1ebeU", row -> row.get("reference"), 160);
        WalletUi.column(entries, "TH\u1edcI GIAN", row -> time(row.get("time")), 150);
        for (int index = 0; index < number(data, "entryCount"); index++) {
            String prefix = "entry." + index + "."; Map<String, String> row = new LinkedHashMap<>();
            data.forEach((key, value) -> { if (key.startsWith(prefix)) { row.put(key.substring(prefix.length()), value); } }); entries.getItems().add(row);
        }
        entries.setPlaceholder(new Label("Kh\u00f4ng c\u00f3 giao d\u1ecbch v\u00ed c\u1ee7a b\u1ea1n trong phi\u00ean."));
        Button allWallet = new Button("M\u1edf v\u00ed c\u1ee7a t\u00f4i"); allWallet.setId("receipt-open-wallet"); allWallet.setOnAction(event -> { if (active()) { openWallet.run(); } });
        transactions.getChildren().setAll(entries, new Label("true".equals(data.get("entriesHasMore")) ? "Hi\u1ec3n th\u1ecb 100 giao d\u1ecbch g\u1ea7n nh\u1ea5t; m\u1edf v\u00ed \u0111\u1ec3 tra c\u1ee9u th\u00eam." : "Ch\u1ec9 giao d\u1ecbch c\u1ee7a t\u00e0i kho\u1ea3n \u0111ang \u0111\u0103ng nh\u1eadp."), allWallet);
        ledger.setOnAction(event -> { transactions.setVisible(!transactions.isVisible()); transactions.setManaged(transactions.isVisible()); });
        Label policy = DesktopTheme.label("Bi\u00ean nh\u1eadn kh\u00f4ng c\u1ea5p quy\u1ec1n m\u1edf l\u1ea1i ph\u00f2ng ri\u00eang, chat hay h\u1ed3 s\u01a1 ng\u01b0\u1eddi kh\u00e1c.", "muted"); policy.setWrapText(true);
        detail.getChildren().setAll(title, personal, result, winner, price, stats, wallet, ledger, transactions, policy);
    }
    private static int number(Map<String, String> data, String key) { return Integer.parseInt(data.getOrDefault(key, "0")); }
    private static String money(String raw) { return raw == null || raw.isBlank() ? "--" : WalletUi.money(new BigDecimal(raw)); }
    private static String time(String raw) {
        return raw == null || raw.isBlank() ? "--" : java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault()).format(Instant.parse(raw));
    }
    public static String outcomeText(String raw) {
        return switch (raw == null ? "" : raw) { case "WON" -> "K\u1ebft th\u00fac c\u00f3 ng\u01b0\u1eddi th\u1eafng"; case "NO_BIDS" -> "K\u1ebft th\u00fac kh\u00f4ng c\u00f3 bid"; case "CANCELLED" -> "Phi\u00ean b\u1ecb h\u1ee7y"; default -> "T\u1ea5t c\u1ea3 tr\u1ea1ng th\u00e1i"; };
    }
    public static String roleText(String raw) {
        return switch (raw == null ? "" : raw) { case "WINNER" -> "B\u1ea1n \u0111\u00e3 th\u1eafng"; case "NOT_WON" -> "B\u1ea1n kh\u00f4ng th\u1eafng"; case "HOST" -> "B\u1ea1n l\u00e0 ch\u1ee7 ph\u00f2ng"; default -> "B\u1ea1n ch\u1ec9 theo d\u00f5i"; };
    }
    private static String walletText(String raw) {
        return switch (raw == null ? "" : raw) { case "PAID" -> "\u0110\u00e3 thanh to\u00e1n (ledger x\u00e1c nh\u1eadn)"; case "RELEASED" -> "\u0110\u00e3 gi\u1ea3i ph\u00f3ng gi\u1eef ch\u1ed7"; case "REVIEW_REQUIRED" -> "C\u1ea7n \u0111\u1ed1i so\u00e1t, ch\u01b0a x\u00e1c nh\u1eadn thanh to\u00e1n"; default -> "Kh\u00f4ng c\u00f3 giao d\u1ecbch li\u00ean quan"; };
    }
    private static StringConverter<String> converter(boolean scopes) {
        return new StringConverter<>() {
            @Override public String toString(String value) { return !scopes ? outcomeText(value) : switch (value == null ? "" : value) {
                case "WON" -> "Phi\u00ean t\u00f4i \u0111\u00e3 th\u1eafng"; case "HOSTED" -> "Phi\u00ean t\u00f4i t\u1ed5 ch\u1ee9c"; default -> "Phi\u00ean t\u00f4i \u0111\u00e3 tham gia"; }; }
            @Override public String fromString(String value) { return value; }
        };
    }
}
