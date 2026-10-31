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

public final class WalletPane extends BorderPane implements AutoCloseable {
    private final boolean embedded;
    private boolean embeddedActive;
    private final WalletApi api;
    private final long userId;
    private final String username;
    private final BooleanSupplier sameSession;
    private final Stage stage = new Stage();
    private final Label balance = DesktopTheme.label("0 \u20ab", "stat-value");
    private final Label available = DesktopTheme.label("0 \u20ab", "stat-value");
    private final TabPane moneyActions = new TabPane();
    private final Label message = DesktopTheme.label("", "muted");
    private final Label qrHint = DesktopTheme.label("Ch\u1ecdn s\u1ed1 ti\u1ec1n v\u00e0 b\u1ea5m Hi\u1ec7n QR thử nghiệm.", "muted");
    private final TextField amount = new TextField("100000");
    private final ImageView qr = new ImageView();
    private final Button generate = new Button("Hi\u1ec7n QR thử nghiệm");
    private final Button confirm = new Button("Đã nạp");
    private final TableView<TopUpRequest> requests = WalletUi.requests(false);
    private final Timeline poll = new Timeline(new KeyFrame(Duration.seconds(2), event -> refresh()));
    private String requestId;
    private BigDecimal requestedAmount;
    private boolean loading, submitting, pending;
    private final TextField withdrawalAmount = new TextField("100000");
    private final Button withdraw = AccountUi.button("Gửi yêu cầu rút", "exchange");
    private final TableView<WithdrawalRequest> withdrawals = WalletUi.withdrawals(false);
    private final Label withdrawalHint = DesktopTheme.label("Chỉ trừ tiền khi máy chủ duyệt yêu cầu.", "account-note");
    private boolean withdrawing, withdrawalPending;
    private BigDecimal availableFunds, withdrawalRequestedAmount;
    private String withdrawalRequestId;
    private long walletGeneration;

    public WalletPane(Window owner, WalletApi api, long userId, String username, BooleanSupplier sameSession) {
        this(owner, api, userId, username, sameSession, false);
    }
    public WalletPane(Window owner, WalletApi api, long userId, String username, BooleanSupplier sameSession, boolean embedded) {
        this.api = api; this.userId = userId; this.username = username; this.sameSession = sameSession; this.embedded = embedded;
        stage.initOwner(owner); stage.initModality(Modality.WINDOW_MODAL); stage.setTitle("Ví của " + username);
        getStyleClass().addAll("account-page", "wallet-page"); setId("account-wallet");
        HBox stats = new HBox(12, stat("Số dư", balance, "Tổng số tiền trong ví của bạn", "coins"),
                stat("Khả dụng", available, "Có thể dùng để đấu giá hoặc rút tiền mô phỏng", "wallet"));
        Button retry = AccountUi.button("Làm mới / Thử lại", "refresh"); retry.setId("wallet-refresh"); retry.getStyleClass().add("account-gold-soft");
        retry.setOnAction(event -> refresh());
        Label explanation = DesktopTheme.label("Số dư khả dụng dùng để đấu giá hoặc rút tiền mô phỏng. Xem lịch sử cộng/trừ tiền tại mục Giao dịch.", "account-note");
        HBox refreshRow = new HBox(16, retry, explanation); refreshRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT); HBox.setHgrow(explanation, Priority.ALWAYS);
        VBox heading = new VBox(12, AccountUi.banner("Ví của " + username, "Mô phỏng học tập - không chuyển tiền thật", "Quản lý số dư\nTham gia đấu giá dễ dàng"), stats, refreshRow);
        heading.getStyleClass().add("wallet-heading"); setTop(heading);
        qr.setFitWidth(180); qr.setFitHeight(180); qr.setSmooth(false); qr.setId("wallet-qr-image");
        VBox placeholder = new VBox(14, AccountUi.emblem("qr", 76), DesktopTheme.label("QR thanh toán (thử nghiệm)", "account-section-title"), DesktopTheme.label("Sẽ hiển thị mã QR mẫu ở đây", "account-note"));
        placeholder.setAlignment(javafx.geometry.Pos.CENTER); placeholder.visibleProperty().bind(qr.imageProperty().isNull()); placeholder.managedProperty().bind(placeholder.visibleProperty());
        StackPane qrFrame = new StackPane(placeholder, qr); qrFrame.getStyleClass().add("wallet-qr-frame"); qrFrame.setMinHeight(210); qrFrame.setPrefHeight(230); qrFrame.setMaxHeight(240);
        confirm.setText("Đã nạp"); confirm.setGraphic(MarketplaceIcons.icon("check", 14, "#92602a")); confirm.getStyleClass().add("account-button");
        confirm.setDisable(true); generate.setGraphic(MarketplaceIcons.icon("qr", 15, "#fff8ec")); generate.getStyleClass().addAll("account-button", "account-gold");
        generate.setId("wallet-generate"); confirm.setId("wallet-confirm");
        amount.setPromptText("1.000 - 1.000.000.000 đồng"); amount.setId("wallet-amount"); amount.setMinWidth(0); HBox.setHgrow(amount, Priority.ALWAYS);
        Label currency = DesktopTheme.label("₫", "wallet-currency"); HBox amountBox = new HBox(amount, currency); amountBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT); amountBox.getStyleClass().add("wallet-amount-box");
        HBox depositActions = new HBox(9, generate, confirm); generate.setMaxWidth(Double.MAX_VALUE); confirm.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(generate, Priority.ALWAYS); HBox.setHgrow(confirm, Priority.ALWAYS);
        qrHint.getStyleClass().add("account-note"); qrHint.setWrapText(true);
        VBox deposit = AccountUi.card(AccountUi.sectionHeading("Nạp tiền giả lập", "Nhập số tiền để tạo yêu cầu nạp (thử nghiệm)", "wallet"), amountBox, depositActions, qrFrame,
                AccountUi.hint("Chọn số tiền và bấm Hiện QR thử nghiệm.\nQR chỉ chứa mã thử nghiệm. Bấm Đã nạp để gửi yêu cầu; số dư chỉ tăng khi máy chủ duyệt."), qrHint);
        deposit.setPrefWidth(290); deposit.setMinWidth(290); deposit.setMaxWidth(300); deposit.setId("wallet-deposit");
        withdrawalAmount.setId("wallet-withdrawal-amount"); withdrawalAmount.setPromptText("1.000 - 1.000.000.000 đồng");
        withdrawalAmount.setMinWidth(0); HBox.setHgrow(withdrawalAmount, Priority.ALWAYS);
        HBox withdrawalAmountBox = new HBox(withdrawalAmount, DesktopTheme.label("₫", "wallet-currency"));
        withdrawalAmountBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT); withdrawalAmountBox.getStyleClass().add("wallet-amount-box");
        withdraw.setId("wallet-withdraw"); withdraw.getStyleClass().add("account-gold"); withdraw.setMaxWidth(Double.MAX_VALUE); withdraw.setDisable(true);
        withdraw.setOnAction(event -> submitWithdrawal()); withdrawalHint.setWrapText(true);
        VBox withdrawalCard = AccountUi.card(AccountUi.sectionHeading("Rút tiền giả lập", "Gửi yêu cầu để máy chủ duyệt rút tiền", "exchange"),
                withdrawalAmountBox, withdraw, AccountUi.hint("Tiền chỉ bị trừ sau khi được duyệt. Không thể rút khoản đang giữ chỗ đấu giá."), withdrawalHint);
        withdrawalCard.setId("wallet-withdrawal");
        withdrawalAmount.textProperty().addListener((observable, before, now) -> {
            if (!withdrawing && !withdrawalPending) { withdrawalRequestId = null; withdrawalRequestedAmount = null; }
        });
        configureTables();
        Button requestRefresh = AccountUi.button("", "refresh"); requestRefresh.getStyleClass().add("account-close"); requestRefresh.setAccessibleText("Tải lại yêu cầu nạp tiền"); requestRefresh.setOnAction(event -> refresh());
        HBox requestHeading = AccountUi.sectionHeading("Yêu cầu nạp tiền", "Danh sách các yêu cầu nạp tiền giả lập của bạn", "receipt"); requestHeading.getChildren().add(requestRefresh);
        VBox requestsCard = AccountUi.card(requestHeading, requests); requestsCard.setId("wallet-requests-card");
        VBox withdrawalsCard = AccountUi.card(AccountUi.sectionHeading("Yêu cầu rút tiền", "Theo dõi trạng thái duyệt rút tiền của bạn", "exchange"), withdrawals);
        withdrawalsCard.setId("wallet-withdrawals-card");
        HBox depositContent = new HBox(14, deposit, requestsCard); HBox.setHgrow(requestsCard, Priority.ALWAYS);
        HBox withdrawalContent = new HBox(14, withdrawalCard, withdrawalsCard); HBox.setHgrow(withdrawalsCard, Priority.ALWAYS);
        withdrawalCard.setMinWidth(290); withdrawalCard.setPrefWidth(300); withdrawalCard.setMaxWidth(310);
        Tab depositTab = new Tab("Nạp tiền", depositContent), withdrawalTab = new Tab("Rút tiền", withdrawalContent);
        for (Tab tab : java.util.List.of(depositTab, withdrawalTab)) { tab.setClosable(false); }
        withdrawalTab.setId("wallet-withdrawal-tab"); moneyActions.setId("wallet-money-actions");
        moneyActions.getTabs().setAll(depositTab, withdrawalTab);
        VBox content = new VBox(moneyActions); VBox.setVgrow(moneyActions, Priority.ALWAYS); content.getStyleClass().add("wallet-content"); content.setMinHeight(490);
        ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.setId("wallet-body-scroll");
        content.minHeightProperty().bind(javafx.beans.binding.Bindings.max(490, scroll.heightProperty().subtract(3))); setCenter(scroll);
        message.getStyleClass().add("wallet-message"); message.setWrapText(true); setBottom(message);
        message.visibleProperty().bind(message.textProperty().isNotEmpty()); message.managedProperty().bind(message.visibleProperty());
        amount.textProperty().addListener((observable, before, now) -> { if (!pending && !submitting) { requestId = null; requestedAmount = null; qr.setImage(null); confirm.setDisable(true); } });
        generate.setOnAction(event -> generate()); confirm.setOnAction(event -> submit());
        if (!embedded) {
            Scene scene = new Scene(DesktopTheme.viewport(this), 1100, 800); DesktopTheme.apply(scene); stage.setScene(scene); stage.setMinWidth(850); stage.setMinHeight(520); stage.setOnHidden(event -> close());
        }
        poll.setCycleCount(Timeline.INDEFINITE);
    }
    private void configureTables() {
        requests.setId("wallet-requests"); withdrawals.setId("wallet-withdrawals");
        configureRequestTable(requests); configureRequestTable(withdrawals);
        requests.setPlaceholder(DesktopTheme.label("Chưa có yêu cầu nạp tiền", "account-note"));
        withdrawals.setPlaceholder(DesktopTheme.label("Chưa có yêu cầu rút tiền", "account-note"));
    }
    @SuppressWarnings("unchecked")
    private <Row> void configureRequestTable(TableView<Row> table) {
        table.getStyleClass().add("account-table"); table.setFixedCellSize(43);
        table.setPrefHeight(145); table.setMinHeight(130); table.setMaxHeight(170);
        ((TableColumn<Row, String>) table.getColumns().get(3)).setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(String id, boolean empty) {
                super.updateItem(id, empty); setText(null); setGraphic(null);
                if (!empty && id != null) {
                    Label value = new Label(id); value.setMaxWidth(Double.MAX_VALUE); value.setMinWidth(0); HBox.setHgrow(value, Priority.ALWAYS); value.setTooltip(new Tooltip(id));
                    Button copy = AccountUi.button("", "copy"); copy.getStyleClass().add("wallet-copy"); copy.setAccessibleText("Sao chép mã yêu cầu");
                    copy.setOnAction(event -> { var data = new javafx.scene.input.ClipboardContent(); data.putString(id); javafx.scene.input.Clipboard.getSystemClipboard().setContent(data); message.setText("Đã sao chép mã yêu cầu."); });
                    HBox row = new HBox(6, value, copy); row.setAlignment(javafx.geometry.Pos.CENTER_LEFT); setGraphic(row);
                }
            }
        });
        ((TableColumn<Row, String>) table.getColumns().get(1)).setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty); setText(null); setGraphic(null);
                if (!empty && text != null) {
                    Label dot = new Label("●"); dot.getStyleClass().add(text.equals("Đã cộng tiền") || text.equals("Đã trừ tiền") ? "account-status-green" : text.equals("Chờ duyệt") ? "account-status-gold" : "account-status-red");
                    HBox row = new HBox(7, dot, new Label(text)); row.setAlignment(javafx.geometry.Pos.CENTER_LEFT); setGraphic(row);
                }
            }
        });
    }
    public void show() { DesktopTheme.fitOnScreen(stage, 1100, 820); stage.show(); poll.play(); refresh(); }
    public void startEmbedded() { if (!embedded) { throw new IllegalStateException("Standalone wallet"); } embeddedActive = true; poll.play(); refresh(); }
    private VBox stat(String title, Label value, String note, String icon) {
        value.getStyleClass().add("wallet-stat-value");
        value.setWrapText(false); value.setMinWidth(0); value.setMaxWidth(Double.MAX_VALUE);
        Tooltip fullValue = new Tooltip(); fullValue.textProperty().bind(value.textProperty()); value.setTooltip(fullValue);
        Label info = new Label(title); info.setGraphic(MarketplaceIcons.icon("info", 12, "#a68453")); info.setContentDisplay(ContentDisplay.RIGHT); info.setGraphicTextGap(7);
        HBox row = new HBox(8, AccountUi.emblem(icon, 30), info); row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox card = AccountUi.card(row, value, DesktopTheme.label(note, "account-note")); card.getStyleClass().add("wallet-stat-card");
        card.setMinWidth(0); card.setPrefWidth(0); card.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(card, Priority.ALWAYS); return card;
    }
    private boolean active() {
        if (!sameSession.getAsBoolean()) { if (embedded) { close(); } else { stage.close(); } return false; }
        return embedded ? embeddedActive && getScene() != null : stage.isShowing();
    }
    @Override public void close() {
        embeddedActive = false; poll.stop(); walletGeneration++; requests.getItems().clear(); withdrawals.getItems().clear(); qr.setImage(null);
        availableFunds = null; withdrawalRequestId = null; withdrawalRequestedAmount = null;
        requestId = null; requestedAmount = null; balance.setText("—"); available.setText("—");
    }
    private void generate() {
        if (!active() || pending || submitting || withdrawing) { return; }
        try {
            BigDecimal value = new BigDecimal(amount.getText().trim()).setScale(2, RoundingMode.UNNECESSARY);
            if (value.compareTo(new BigDecimal("1000")) < 0 || value.compareTo(new BigDecimal("1000000000")) > 0) { throw new IllegalArgumentException(); }
            requestId = UUID.randomUUID().toString(); requestedAmount = value; renderQr(); confirm.setDisable(false);
            qrHint.setText("Thử nghiệm " + WalletUi.money(value) + " cho " + username + ". Qu\u00e9t QR ho\u1eb7c b\u1ea5m Đã nạp \u0111\u1ec3 gi\u1ea3 l\u1eadp.");
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
        } catch (com.google.zxing.WriterException exception) { throw new IllegalStateException("Cannot create thử nghiệm QR", exception); }
    }
    private void submit() {
        if (!active() || submitting || withdrawing || pending || requestId == null) { return; }
        walletGeneration++; submitting = true; controls(); message.setText("\u0110ang g\u1eedi y\u00eau c\u1ea7u...");
        api.submit(requestId, requestedAmount).whenComplete((response, error) -> Platform.runLater(() -> {
            submitting = false; if (!active()) { return; }
            if (error != null || response == null || !response.isSuccess()) { message.setText(error == null && response != null ? response.getUserMessage() : "Ch\u01b0a nh\u1eadn ph\u1ea3n h\u1ed3i. B\u1ea5m l\u1ea1i Đã nạp \u0111\u1ec3 th\u1eed l\u1ea1i c\u00f9ng m\u00e3, kh\u00f4ng n\u1ea1p tr\u00f9ng."); controls(); }
            else { apply(response); message.setText("\u0110\u00e3 g\u1eedi. Ch\u1edd ng\u01b0\u1eddi v\u1eadn h\u00e0nh máy chủ ch\u1ea5p nh\u1eadn y\u00eau c\u1ea7u thử nghiệm."); }
        }));
    }
    private void refresh() {
        if (!active() || loading || submitting || withdrawing) { return; } loading = true;
        long generation = walletGeneration;
        api.get().whenComplete((response, error) -> Platform.runLater(() -> {
            loading = false; if (!active() || generation != walletGeneration) { return; }
            if (error != null || response == null || !response.isSuccess()) { message.setText("Kh\u00f4ng t\u1ea3i \u0111\u01b0\u1ee3c v\u00ed. Ki\u1ec3m tra k\u1ebft n\u1ed1i máy chủ."); return; }
            apply(response);
        }));
    }
    private void apply(ApiResponse response) {
        if (!Long.toString(userId).equals(response.get("userId"))) { stage.close(); return; }
        balance.setText(WalletUi.money(new BigDecimal(response.get("balance"))));
        available.setText(WalletUi.money(new BigDecimal(response.get("available"))));
        availableFunds = new BigDecimal(response.get("available"));
        var data = response.getWireMessage().getData(); var values = WalletWire.requests(data);
        var withdrawalValues = WalletWire.withdrawals(data); withdrawals.getItems().setAll(withdrawalValues);
        WithdrawalRequest waitingWithdrawal = withdrawalValues.stream().filter(value -> value.status().equals("PENDING")).findFirst().orElse(null);
        boolean wasWithdrawalPending = withdrawalPending; withdrawalPending = waitingWithdrawal != null;
        if (withdrawalPending) {
            withdrawalRequestId = waitingWithdrawal.id(); withdrawalRequestedAmount = waitingWithdrawal.amount();
            withdrawalAmount.setText(waitingWithdrawal.amount().toPlainString());
            withdrawalHint.setText("Đang chờ duyệt rút " + WalletUi.money(waitingWithdrawal.amount()) + ". Số dư chưa bị trừ.");
        } else if (wasWithdrawalPending) {
            withdrawalRequestId = null; withdrawalRequestedAmount = null;
            withdrawalHint.setText("Yêu cầu đã được xử lý. Xem trạng thái và lý do trong bảng rút tiền.");
        }
        requests.getItems().setAll(values);
        TopUpRequest waiting = values.stream().filter(value -> value.status().equals("PENDING")).findFirst().orElse(null);
        boolean wasPending = pending; pending = waiting != null;
        if (pending) {
            requestId = waiting.id(); requestedAmount = waiting.amount(); renderQr();
            qrHint.setText("\u0110ang ch\u1edd máy chủ duy\u1ec7t " + WalletUi.money(waiting.amount()) + ". Kh\u00f4ng c\u1ea7n qu\u00e9t l\u1ea1i.");
        } else if (wasPending) {
            requestId = null; requestedAmount = null; qr.setImage(null); qrHint.setText("Y\u00eau c\u1ea7u \u0111\u00e3 \u0111\u01b0\u1ee3c x\u1eed l\u00fd. Xem tr\u1ea1ng th\u00e1i trong b\u1ea3ng.");
            message.setText("S\u1ed1 d\u01b0 \u0111\u00e3 \u0111\u1ed3ng b\u1ed9 t\u1eeb máy chủ.");
        }
        controls();
    }
    private void controls() {
        boolean busy = submitting || withdrawing;
        amount.setDisable(busy || pending); generate.setDisable(busy || pending); confirm.setDisable(busy || pending || requestId == null);
        withdrawalAmount.setDisable(busy || withdrawalPending); withdraw.setDisable(busy || withdrawalPending || availableFunds == null);
    }

    private void submitWithdrawal() {
        if (!active() || submitting || withdrawing || withdrawalPending || availableFunds == null) { return; }
        try {
            if (withdrawalRequestId == null) {
                BigDecimal value = new BigDecimal(withdrawalAmount.getText().strip()).setScale(2, RoundingMode.UNNECESSARY);
                if (value.compareTo(new BigDecimal("1000")) < 0 || value.compareTo(new BigDecimal("1000000000")) > 0) { throw new IllegalArgumentException(); }
                if (value.compareTo(availableFunds) > 0) { message.setText("Số dư khả dụng không đủ để rút số tiền này."); return; }
                withdrawalRequestedAmount = value; withdrawalRequestId = UUID.randomUUID().toString();
            }
        } catch (RuntimeException error) { message.setText("Tiền rút từ 1.000 đến 1.000.000.000 đồng; tối đa 2 chữ số thập phân."); return; }
        withdrawing = true; walletGeneration++; controls(); message.setText("Đang gửi yêu cầu rút tiền…");
        api.withdraw(withdrawalRequestId, withdrawalRequestedAmount).whenComplete((response, error) -> Platform.runLater(() -> {
            withdrawing = false; if (!active()) { return; }
            if (error != null || response == null || !response.isSuccess()) {
                message.setText(error == null && response != null ? response.getUserMessage() : "Chưa nhận phản hồi. Bấm gửi lại để thử cùng mã, không tạo yêu cầu trùng.");
                controls();
            } else {
                apply(response); message.setText("Đã gửi yêu cầu rút. Chỉ trừ tiền khi máy chủ duyệt.");
            }
        }));
    }

}
