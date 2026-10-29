package vn.ptit.btl16.client.fx;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.fx.WalletUi;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** PINs are cleared after each request. Open-vault access is discarded when this tab is left. */
public final class VaultPane extends VBox implements AutoCloseable {
    private final WalletApi api;
    private final long user;
    private final BooleanSupplier sameSession;
    private final Runnable moneyChanged;
    private final Label balance = DesktopTheme.label("—", "vault-value"), available = DesktopTheme.label("—", "vault-value");
    private final Label state = DesktopTheme.label("Đã khóa", "pill"), message = DesktopTheme.label("", "account-note");
    private final TextField initialAmount = amountField("vault-initial-amount"), depositAmount = amountField("vault-deposit-amount"), withdrawalAmount = amountField("vault-withdrawal-amount");
    private final PasswordField newPin = pin("vault-new-pin"), confirmPin = pin("vault-confirm-pin"), unlockPin = pin("vault-unlock-pin"), withdrawalPin = pin("vault-withdrawal-pin");
    private final Button initialize = AccountUi.button("Tạo PIN và chuyển vào két", "lock"), unlock = AccountUi.button("Mở két", "lock"), deposit = AccountUi.button("Chuyển vào két", "enter"), withdraw = AccountUi.button("Rút từ két về ví", "exchange"), lock = AccountUi.button("Khóa két", "lock");
    private final VBox setup, locked, opened;
    private final Timeline poll = new Timeline(new KeyFrame(Duration.seconds(3), event -> refresh()));
    private boolean active, ready, configured, unlocked, busy, loading;
    private String token = "", initialId, depositId, withdrawalId;
    private long lifecycle, revision;

    public VaultPane(WalletApi api, long user, BooleanSupplier sameSession, Runnable moneyChanged) {
        this.api = api; this.user = user; this.sameSession = sameSession; this.moneyChanged = moneyChanged;
        setId("wallet-vault"); getStyleClass().add("vault-pane"); setSpacing(16); setPadding(new javafx.geometry.Insets(18));
        HBox heading = AccountUi.heading("Két sắt của tôi", "Cất tiền riêng và bảo vệ bằng mã PIN 6 số", "lock", null); heading.getChildren().addAll(state, lock);
        lock.setId("vault-lock"); lock.setOnAction(event -> lock());
        VBox vaultBalance = AccountUi.card(DesktopTheme.label("Tiền đang trong két", "account-section-title"), balance, DesktopTheme.label("Không dùng để đặt giá hoặc rút tiền ngoài ví", "account-note"));
        VBox walletAvailable = AccountUi.card(DesktopTheme.label("Khả dụng trong ví", "account-section-title"), available, DesktopTheme.label("Có thể chuyển vào két, không gồm tiền đang giữ chỗ", "account-note"));
        HBox stats = new HBox(14, vaultBalance, walletAvailable); HBox.setHgrow(vaultBalance, Priority.ALWAYS); HBox.setHgrow(walletAvailable, Priority.ALWAYS);
        initialize.setId("vault-initialize"); unlock.setId("vault-unlock"); deposit.setId("vault-deposit"); withdraw.setId("vault-withdraw");
        for (Button button : java.util.List.of(initialize, unlock, deposit, withdraw)) { button.getStyleClass().add("account-gold"); button.setMaxWidth(Double.MAX_VALUE); }
        setup = AccountUi.card(AccountUi.sectionHeading("Tạo két trong lần chuyển đầu tiên", "Tạo mã PIN và chuyển tiền vào két cùng một lần", "lock"),
                label("Số tiền chuyển từ ví vào két"), initialAmount, label("Mã PIN mới (6 số)"), newPin, label("Nhập lại mã PIN"), confirmPin, initialize);
        locked = AccountUi.card(AccountUi.sectionHeading("Mở khóa két", "Nhập PIN một lần để sử dụng két trong lần mở này", "lock"), unlockPin, unlock);
        VBox depositCard = AccountUi.card(AccountUi.sectionHeading("Chuyển vào két", "Chuyển từ số dư khả dụng trong ví", "enter"), depositAmount, deposit);
        VBox withdrawalCard = AccountUi.card(AccountUi.sectionHeading("Rút từ két về ví", "Xác nhận PIN thêm một lần cho mỗi lần rút", "exchange"), withdrawalAmount, withdrawalPin, withdraw);
        HBox transfers = new HBox(14, depositCard, withdrawalCard); HBox.setHgrow(depositCard, Priority.ALWAYS); HBox.setHgrow(withdrawalCard, Priority.ALWAYS);
        opened = new VBox(12, transfers, AccountUi.hint("Tiền chuyển giữa ví và két được xử lý ngay, không cần Server duyệt. Rời mục Két sắt sẽ tự khóa két."));
        setup.setMaxWidth(530); locked.setMaxWidth(530); message.setWrapText(true);
        getChildren().addAll(heading, stats, setup, locked, opened, message);
        initialize.setOnAction(event -> initialize()); unlock.setOnAction(event -> unlock()); deposit.setOnAction(event -> transfer(true)); withdraw.setOnAction(event -> transfer(false));
        initialAmount.textProperty().addListener((o, before, value) -> { if (!busy) { initialId = null; } });
        depositAmount.textProperty().addListener((o, before, value) -> { if (!busy) { depositId = null; } });
        withdrawalAmount.textProperty().addListener((o, before, value) -> { if (!busy) { withdrawalId = null; } });
        poll.setCycleCount(Timeline.INDEFINITE); render();
    }
    public void start() {
        if (active) { return; }
        active = true; lifecycle++; token = ""; ready = false; busy = false; loading = false; unlocked = false;
        clearPins(); message.setText("Đang tải két sắt…"); render(); poll.play(); refresh();
        if (getScene() == null) { Platform.runLater(this::refresh); }
    }
    private boolean active() { return active && sameSession.getAsBoolean() && getScene() != null; }
    @Override public void close() {
        active = false; lifecycle++; poll.stop(); clearPins();
        String previous = token; token = ""; unlocked = false; ready = false; balance.setText("—"); available.setText("—");
        if (!previous.isEmpty() && sameSession.getAsBoolean()) { api.lockVault(previous); }
        render();
    }
    private void lock() {
        if (!active() || busy) { return; }
        String previous = token; token = ""; unlocked = false; clearPins(); revision++; render();
        if (!previous.isEmpty()) { api.lockVault(previous); }
        message.setText("Đã khóa két. Nhập PIN khi cần mở lại.");
    }
    private void refresh() {
        if (!active() || busy || loading) { return; }
        loading = true; long life = lifecycle, version = revision;
        api.vault(token).whenComplete((response, error) -> Platform.runLater(() -> {
            if (!active() || life != lifecycle) { return; } loading = false;
            if (version != revision) { return; }
            if (error != null || response == null || !response.isSuccess()) { message.setText("Không tải được két. Kiểm tra kết nối Server."); return; }
            apply(response); if (message.getText().equals("Đang tải két sắt…")) { message.setText(""); }
        }));
    }
    private void apply(ApiResponse response) {
        if (!Long.toString(user).equals(response.get("userId"))) { close(); return; }
        ready = true; configured = Boolean.parseBoolean(response.get("configured")); unlocked = Boolean.parseBoolean(response.get("unlocked")); token = response.get("vaultToken");
        balance.setText(WalletUi.money(new BigDecimal(response.get("vaultBalance")))); available.setText(WalletUi.money(new BigDecimal(response.get("available")))); render();
    }
    private void initialize() {
        if (!active() || busy || !ready || configured) { return; }
        try {
            BigDecimal amount = amount(initialAmount); String secret = requirePin(newPin);
            if (!secret.equals(confirmPin.getText())) { message.setText("Hai lần nhập mã PIN chưa khớp."); return; }
            if (initialId == null) { initialId = UUID.randomUUID().toString(); }
            run(api.initializeVault(initialId, amount, secret, confirmPin.getText()), "Đã tạo PIN và chuyển tiền vào két.", true, () -> initialId = null);
            clearPins();
        } catch (IllegalArgumentException error) { message.setText(error.getMessage()); }
    }
    private void unlock() {
        if (!active() || busy || !ready || unlocked) { return; }
        try { run(api.unlockVault(requirePin(unlockPin)), "Đã mở két. Mỗi lần rút cần xác nhận PIN thêm một lần.", false, () -> { }); clearPins(); }
        catch (IllegalArgumentException error) { message.setText(error.getMessage()); }
    }
    private void transfer(boolean toVault) {
        if (!active() || busy || !unlocked) { return; }
        try {
            BigDecimal amount = amount(toVault ? depositAmount : withdrawalAmount); String secret = toVault ? "" : requirePin(withdrawalPin);
            if (toVault && depositId == null) { depositId = UUID.randomUUID().toString(); }
            if (!toVault && withdrawalId == null) { withdrawalId = UUID.randomUUID().toString(); }
            run(api.transferVault(toVault ? depositId : withdrawalId, toVault ? "DEPOSIT" : "WITHDRAW", amount, token, secret),
                    toVault ? "Đã chuyển tiền từ ví vào két." : "Đã rút tiền từ két về ví.", true, () -> { if (toVault) { depositId = null; } else { withdrawalId = null; } });
            clearPins();
        } catch (IllegalArgumentException error) { message.setText(error.getMessage()); }
    }
    private void run(CompletableFuture<ApiResponse> request, String success, boolean changesMoney, Runnable completed) {
        busy = true; revision++; long life = lifecycle; render(); message.setText("Đang xử lý…");
        request.whenComplete((response, error) -> Platform.runLater(() -> {
            if (!active() || life != lifecycle) {
                if (response != null && response.isSuccess() && !response.get("vaultToken").isEmpty() && sameSession.getAsBoolean()) { api.lockVault(response.get("vaultToken")); }
                return;
            }
            busy = false;
            if (error != null || response == null || !response.isSuccess()) {
                if (response != null && ("VAULT_LOCKED".equals(response.getErrorCode()) || "VAULT_PIN_LOCKED".equals(response.getErrorCode()))) { token = ""; unlocked = false; }
                message.setText(error != null || response == null ? "Chưa nhận phản hồi. Nhập lại PIN nếu cần rồi bấm thử lại; cùng mã giao dịch sẽ không chuyển trùng tiền." : response.getMessageText()); render();
            } else { completed.run(); apply(response); message.setText(success); if (changesMoney) { moneyChanged.run(); } }
        }));
    }
    private void render() {
        visible(setup, ready && !configured); visible(locked, ready && configured && !unlocked); visible(opened, ready && configured && unlocked);
        state.setText(!ready ? "Đang tải" : !configured ? "Chưa tạo PIN" : unlocked ? "Đã mở khóa" : "Đã khóa");
        lock.setVisible(unlocked); lock.setManaged(unlocked); lock.setDisable(busy);
        for (Control control : java.util.List.of(initialAmount, depositAmount, withdrawalAmount, newPin, confirmPin, unlockPin, withdrawalPin, initialize, unlock, deposit, withdraw)) { control.setDisable(busy || !ready); }
    }
    private void clearPins() { newPin.clear(); confirmPin.clear(); unlockPin.clear(); withdrawalPin.clear(); }
    private static void visible(javafx.scene.Node node, boolean value) { node.setVisible(value); node.setManaged(value); }
    private static Label label(String value) { return DesktopTheme.label(value, "account-note"); }
    private static TextField amountField(String id) { TextField field = new TextField(); field.setId(id); field.setPromptText("Nhập số tiền (đồng)"); field.setMaxWidth(Double.MAX_VALUE); return field; }
    private static PasswordField pin(String id) {
        PasswordField field = new PasswordField(); field.setId(id); field.setPromptText("Mã PIN 6 số");
        field.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("[0-9]{0,6}") ? change : null)); return field;
    }
    private static String requirePin(PasswordField field) { String value = field.getText(); if (!value.matches("[0-9]{6}")) { throw new IllegalArgumentException("Nhập đủ mã PIN 6 số."); } return value; }
    private static BigDecimal amount(TextField field) {
        try { BigDecimal value = new BigDecimal(field.getText().strip()).setScale(2, RoundingMode.UNNECESSARY); if (value.signum() <= 0) { throw new NumberFormatException(); } return value; }
        catch (RuntimeException error) { throw new IllegalArgumentException("Số tiền phải lớn hơn 0, tối đa hai chữ số thập phân."); }
    }
}
