package vn.ptit.btl16.client.fx;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import vn.ptit.btl16.client.service.WalletApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.fx.WalletUi;
import vn.ptit.btl16.common.wallet.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class WalletPane extends BorderPane {
    private final WalletApi api;
    private final long userId;
    private final String username;
    private final BooleanSupplier sameSession;
    private final Stage stage = new Stage();
    private final Label balance = DesktopTheme.label("0 \u20ab", "stat-value");
    private final Label available = DesktopTheme.label("0 \u20ab", "stat-value");
    private final Label reserved = DesktopTheme.label("0 \u20ab", "stat-value");
    private final Label message = DesktopTheme.label("", "muted");
    private final Label qrHint = DesktopTheme.label("Ch\u1ecdn s\u1ed1 ti\u1ec1n v\u00e0 b\u1ea5m Hi\u1ec7n QR demo.", "muted");
    private final TextField amount = new TextField("100000");
    private final ImageView qr = new ImageView();
    private final Button generate = new Button("Hi\u1ec7n QR demo");
    private final Button confirm = new Button("\u0110\u00e3 nh\u1eadp");
    private final TableView<TopUpRequest> requests = WalletUi.requests(false);
    private final TableView<WalletEntry> entries = WalletUi.ledger();
    private final Timeline poll = new Timeline(new KeyFrame(Duration.seconds(2), event -> refresh()));
    private String requestId;
    private BigDecimal requestedAmount;
    private boolean loading, submitting, pending;
    private final TextField roomFilter = new TextField();
    private final ComboBox<String> typeFilter = new ComboBox<>();
    private final DatePicker fromFilter = new DatePicker(), toFilter = new DatePicker();
    private final Button nextEntries = new Button("Trang giao dich tiep");
    private java.util.Map<String, String> ledgerFilters = java.util.Map.of();
    private long ledgerGeneration, ledgerCursor;
    private boolean ledgerMode;

    public WalletPane(Window owner, WalletApi api, long userId, String username, BooleanSupplier sameSession) {
        this.api = api; this.userId = userId; this.username = username; this.sameSession = sameSession;
        stage.initOwner(owner); stage.initModality(Modality.WINDOW_MODAL); stage.setTitle("V\u00ed demo - " + username);
        Label disclaimer = DesktopTheme.label("M\u00d4 PH\u1eceNG H\u1eccC T\u1eacP - KH\u00d4NG CHUY\u1ec2N TI\u1ec0N TH\u1eacT", "eyebrow");
        HBox stats = new HBox(12, stat("S\u1ed0 d\u01b0", balance), stat("Kh\u1ea3 d\u1ee5ng", available), stat("\u0110ang gi\u1eef ch\u1ed7", reserved));
        Button retry = new Button("Lam moi / Thu lai"); retry.setOnAction(event -> { refresh(); if (ledgerMode) { loadEntries(false); } });
        VBox heading = new VBox(8, DesktopTheme.label("V\u00ed c\u1ee7a " + username, "page-title"), disclaimer, stats, retry,
                new Label("Kha dung = so du - dang giu. PURCHASE/SALE la thanh toan mo phong ghi trong ledger, khong phai chuyen tien that."));
        heading.setPadding(new Insets(20)); setTop(heading);
        qr.setFitWidth(210); qr.setFitHeight(210); qr.setSmooth(false);
        StackPane qrFrame = new StackPane(qr); qrFrame.getStyleClass().add("wallet-qr"); qrFrame.setMinSize(230, 230); qrFrame.setMaxHeight(230);
        confirm.getStyleClass().add("primary"); confirm.setDisable(true); generate.getStyleClass().add("teal");
        amount.setPromptText("1.000 - 1.000.000.000 \u0111\u1ed3ng");
        VBox deposit = new VBox(12, DesktopTheme.label("N\u1ea1p ti\u1ec1n gi\u1ea3 l\u1eadp", "section-title"), amount, new HBox(8, generate, confirm), qrFrame, qrHint,
                DesktopTheme.label("QR ch\u1ec9 ch\u1ee9a m\u00e3 demo. B\u1ea5m \u0110\u00e3 nh\u1eadp \u0111\u1ec3 g\u1eedi y\u00eau c\u1ea7u; s\u1ed1 d\u01b0 ch\u1ec9 t\u0103ng khi server duy\u1ec7t.", "muted"));
        deposit.getStyleClass().add("card"); deposit.setPrefWidth(290); deposit.setMinWidth(270);
        roomFilter.setPromptText("Ma phien"); roomFilter.setPrefColumnCount(7);
        typeFilter.getItems().setAll("", "TOPUP", "RESERVE", "RELEASE", "PURCHASE", "SALE", "LEGACY_REVIEW"); typeFilter.setValue("");
        fromFilter.setPromptText("Tu ngay"); toFilter.setPromptText("Den ngay (gom ngay nay)");
        Button filter = new Button("Loc / trang dau"); filter.setOnAction(event -> loadEntries(false));
        nextEntries.setDisable(true); nextEntries.setOnAction(event -> loadEntries(true));
        FlowPane filters = new FlowPane(8, 8, roomFilter, typeFilter, fromFilter, toFilter, filter, nextEntries);
        entries.setPlaceholder(new Label("Chua co giao dich phu hop")); requests.setPlaceholder(new Label("Chua co yeu cau nap"));
        VBox history = new VBox(12, DesktopTheme.label("Y\u00eau c\u1ea7u n\u1ea1p ti\u1ec1n", "section-title"), requests,
                DesktopTheme.label("S\u1ed5 giao d\u1ecbch", "section-title"), filters, entries,
                DesktopTheme.label("Khi d\u1eabn \u0111\u1ea7u, ti\u1ec1n b\u1ecb gi\u1eef ch\u1ed7. B\u1ecb v\u01b0\u1ee3t gi\u00e1 s\u1ebd \u0111\u01b0\u1ee3c ho\u00e0n gi\u1eef ch\u1ed7; r\u1eddi ph\u00f2ng kh\u00f4ng h\u1ee7y cam k\u1ebft mua.", "muted"));
        history.getStyleClass().add("card"); history.setMinWidth(420);
        HBox content = new HBox(16, deposit, history); content.setPadding(new Insets(0, 20, 20, 20)); HBox.setHgrow(history, Priority.ALWAYS);
        ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true); setCenter(scroll);
        message.setPadding(new Insets(12, 20, 16, 20)); setBottom(message);
        amount.textProperty().addListener((observable, before, now) -> { if (!pending && !submitting) { requestId = null; requestedAmount = null; qr.setImage(null); confirm.setDisable(true); } });
        generate.setOnAction(event -> generate()); confirm.setOnAction(event -> submit());
        Scene scene = new Scene(this, 1080, 760); DesktopTheme.apply(scene); stage.setScene(scene); stage.setMinWidth(850); stage.setMinHeight(520);
        stage.setOnHidden(event -> poll.stop()); poll.setCycleCount(Timeline.INDEFINITE);
    }

    public void show() { DesktopTheme.fitOnScreen(stage, 1100, 800); stage.show(); poll.play(); refresh(); }
    private VBox stat(String title, Label value) {
        VBox card = new VBox(6, DesktopTheme.label(title, "muted"), value); card.getStyleClass().add("stat-card"); HBox.setHgrow(card, Priority.ALWAYS); return card;
    }
    private boolean active() { if (!sameSession.getAsBoolean()) { stage.close(); return false; } return stage.isShowing(); }
    private void generate() {
        if (!active() || pending || submitting) { return; }
        try {
            BigDecimal value = new BigDecimal(amount.getText().trim()).setScale(2, RoundingMode.UNNECESSARY);
            if (value.compareTo(new BigDecimal("1000")) < 0 || value.compareTo(new BigDecimal("1000000000")) > 0) { throw new IllegalArgumentException(); }
            requestId = UUID.randomUUID().toString(); requestedAmount = value; renderQr(); confirm.setDisable(false);
            qrHint.setText("Demo " + WalletUi.money(value) + " cho " + username + ". Qu\u00e9t QR ho\u1eb7c b\u1ea5m \u0110\u00e3 nh\u1eadp \u0111\u1ec3 gi\u1ea3 l\u1eadp.");
        } catch (RuntimeException exception) { message.setText("Nh\u1eadp s\u1ed1 t\u1eeb 1.000 \u0111\u1ebfn 1.000.000.000; t\u1ed1i \u0111a 2 ch\u1eef s\u1ed1 th\u1eadp ph\u00e2n."); }
    }
    private void renderQr() {
        try {
            var matrix = new QRCodeWriter().encode("BTL16-DEMO|NO_REAL_PAYMENT|user=" + userId + "|request=" + requestId + "|amount=" + requestedAmount.toPlainString(), BarcodeFormat.QR_CODE, 240, 240);
            WritableImage image = new WritableImage(matrix.getWidth(), matrix.getHeight());
            for (int row = 0; row < matrix.getHeight(); row++) { for (int column = 0; column < matrix.getWidth(); column++) {
                image.getPixelWriter().setArgb(column, row, matrix.get(column, row) ? 0xff183d43 : 0xffffffff);
            } }
            qr.setImage(image);
        } catch (com.google.zxing.WriterException exception) { throw new IllegalStateException("Cannot create demo QR", exception); }
    }
    private void submit() {
        if (!active() || submitting || pending || requestId == null) { return; }
        submitting = true; controls(); message.setText("\u0110ang g\u1eedi y\u00eau c\u1ea7u...");
        api.submit(requestId, requestedAmount).whenComplete((response, error) -> Platform.runLater(() -> {
            submitting = false; if (!active()) { return; }
            if (error != null || response == null || !response.isSuccess()) { message.setText(error == null && response != null ? response.getErrorCode() + ": " + response.getMessageText() : "Ch\u01b0a nh\u1eadn ph\u1ea3n h\u1ed3i. B\u1ea5m l\u1ea1i \u0110\u00e3 nh\u1eadp \u0111\u1ec3 th\u1eed l\u1ea1i c\u00f9ng m\u00e3, kh\u00f4ng n\u1ea1p tr\u00f9ng."); controls(); }
            else { apply(response); message.setText("\u0110\u00e3 g\u1eedi. Ch\u1edd ng\u01b0\u1eddi v\u1eadn h\u00e0nh server ch\u1ea5p nh\u1eadn y\u00eau c\u1ea7u demo."); }
        }));
    }
    private void refresh() {
        if (!active() || loading || submitting) { return; } loading = true;
        api.get().whenComplete((response, error) -> Platform.runLater(() -> {
            loading = false; if (!active()) { return; }
            if (error != null || response == null || !response.isSuccess()) { message.setText("Kh\u00f4ng t\u1ea3i \u0111\u01b0\u1ee3c v\u00ed. Ki\u1ec3m tra k\u1ebft n\u1ed1i server."); return; }
            apply(response);
        }));
    }
    private void apply(ApiResponse response) {
        if (!Long.toString(userId).equals(response.get("userId"))) { stage.close(); return; }
        balance.setText(WalletUi.money(new BigDecimal(response.get("balance"))));
        available.setText(WalletUi.money(new BigDecimal(response.get("available")))); reserved.setText(WalletUi.money(new BigDecimal(response.get("reserved"))));
        var data = response.getWireMessage().getData(); var values = WalletWire.requests(data);
        requests.getItems().setAll(values); if (!ledgerMode) { entries.getItems().setAll(WalletWire.entries(data)); }
        TopUpRequest waiting = values.stream().filter(value -> value.status().equals("PENDING")).findFirst().orElse(null);
        boolean wasPending = pending; pending = waiting != null;
        if (pending) {
            requestId = waiting.id(); requestedAmount = waiting.amount(); renderQr();
            qrHint.setText("\u0110ang ch\u1edd server duy\u1ec7t " + WalletUi.money(waiting.amount()) + ". Kh\u00f4ng c\u1ea7n qu\u00e9t l\u1ea1i.");
        } else if (wasPending) {
            requestId = null; requestedAmount = null; qr.setImage(null); qrHint.setText("Y\u00eau c\u1ea7u \u0111\u00e3 \u0111\u01b0\u1ee3c x\u1eed l\u00fd. Xem tr\u1ea1ng th\u00e1i trong b\u1ea3ng.");
            message.setText("S\u1ed1 d\u01b0 \u0111\u00e3 \u0111\u1ed3ng b\u1ed9 t\u1eeb server.");
        }
        controls();
    }
    private void controls() { amount.setDisable(submitting || pending); generate.setDisable(submitting || pending); confirm.setDisable(submitting || pending || requestId == null); }

    private void loadEntries(boolean next) {
        if (!active()) { return; }
        java.util.Map<String, String> filters = new java.util.LinkedHashMap<>();
        try {
            if (next) { filters.putAll(ledgerFilters); filters.put("beforeId", Long.toString(ledgerCursor)); }
            else {
                String room = roomFilter.getText().strip(); if (!room.isEmpty() && Long.parseLong(room) <= 0) { throw new IllegalArgumentException(); }
                filters.put("auctionId", room); filters.put("type", typeFilter.getValue());
                var zone = java.time.ZoneId.systemDefault();
                if (fromFilter.getValue() != null) { filters.put("from", fromFilter.getValue().atStartOfDay(zone).toInstant().toString()); }
                if (toFilter.getValue() != null) { filters.put("to", toFilter.getValue().plusDays(1).atStartOfDay(zone).toInstant().toString()); }
                ledgerFilters = java.util.Map.copyOf(filters);
            }
        } catch (RuntimeException error) { message.setText("Ma phien/ngay loc khong hop le"); return; }
        ledgerMode = true; long generation = ++ledgerGeneration; nextEntries.setDisable(true); message.setText("Dang tai giao dich...");
        api.entries(filters).whenComplete((response, error) -> Platform.runLater(() -> {
            if (!active() || generation != ledgerGeneration) { return; }
            if (error != null || response == null || !response.isSuccess()) {
                message.setText("Khong tai duoc giao dich. Bam Loc / trang dau de thu lai."); return;
            }
            if (!Long.toString(userId).equals(response.get("userId"))) { stage.close(); return; }
            entries.getItems().setAll(WalletWire.entries(response.getWireMessage().getData()));
            ledgerCursor = Long.parseLong(response.get("nextBeforeId")); nextEntries.setDisable(!Boolean.parseBoolean(response.get("hasMore")));
            message.setText(entries.getItems().isEmpty() ? "Khong co giao dich phu hop" : "Giao dich cua chinh ban; loc tren server truoc phan trang.");
        }));
    }
}
