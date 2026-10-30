package vn.ptit.btl16.server.fx;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.fx.WalletUi;
import vn.ptit.btl16.common.wallet.*;
import vn.ptit.btl16.server.ServerApplication;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class ServerWalletWindow extends ServerAdminPage {
    private final Supplier<ServerApplication> server;
    private final Executor worker;
    private Stage stage;
    private final TableView<WalletAccount> accounts = new TableView<>();
    private final TableView<TopUpRequest> pending = WalletUi.requests(true);
    private final TableView<WalletEntry> entries = WalletUi.ledger();
    private final TableView<TopUpRequest> userRequests = WalletUi.requests(false);
    private final Label profile = DesktopTheme.label("Ch\u1ecdn t\u00e0i kho\u1ea3n \u0111\u1ec3 xem chi ti\u1ebft.", "muted");
    private final Label message = DesktopTheme.label("", "muted");
    private final Label pendingCount = DesktopTheme.label("", "pill");
    private final Button approve = new Button("Duyệt nạp");
    private final Button reject = new Button("T\u1eeb ch\u1ed1i");
    private final TextField rejectionReason = new TextField("Yêu cầu nạp mô phỏng chưa được xác nhận");
    private final Timeline poll = new Timeline(new KeyFrame(Duration.seconds(3), event -> refresh()));
    private boolean loading, reviewing, replacing;
    private final TableView<WithdrawalRequest> pendingWithdrawals = WalletUi.withdrawals(true);
    private final TableView<WithdrawalRequest> userWithdrawals = WalletUi.withdrawals(false);
    private final Button approveWithdrawal = new Button("Duyệt rút");
    private final Button rejectWithdrawal = new Button("Từ chối");
    private final TextField withdrawalRejectionReason = new TextField("Yêu cầu rút mô phỏng chưa được xác nhận");
    private long refreshGeneration;

    public ServerWalletWindow(Window owner, Supplier<ServerApplication> server, Executor worker) {
        super(owner);
        this.server = server; this.worker = worker;
        setId("server-wallet-page"); setPadding(new Insets(16, 28, 16, 28));
        WalletUi.column(accounts, "ID", value -> Long.toString(value.userId()), 65);
        WalletUi.column(accounts, "T\u00c0I KHO\u1ea2N", WalletAccount::username, 140);
        WalletUi.column(accounts, "H\u1ecc T\u00caN", WalletAccount::displayName, 170);
        WalletUi.column(accounts, "S\u1ed0 D\u01af", value -> WalletUi.money(value.balance()), 160);
        WalletUi.column(accounts, "GI\u1eee CH\u1ed6", value -> WalletUi.money(value.reserved()), 160);
        WalletUi.column(accounts, "KH\u1ea2 D\u1ee4NG", value -> WalletUi.money(value.available()), 160);
        accounts.setId("server-wallet-accounts");
        accounts.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        accounts.setPrefHeight(210); accounts.setMinHeight(80);
        accounts.setPlaceholder(new Label("Chưa có tài khoản"));
        pending.setId("server-pending-topups");
        pending.setPlaceholder(new Label("Không có yêu cầu nạp chờ duyệt"));
        pendingWithdrawals.setPlaceholder(new Label("Không có yêu cầu rút chờ duyệt"));
        userRequests.setPlaceholder(new Label("Chọn tài khoản để xem lịch sử nạp"));
        userWithdrawals.setPlaceholder(new Label("Chọn tài khoản để xem lịch sử rút"));
        entries.setPlaceholder(new Label("Chọn tài khoản để xem biến động ví"));
        accounts.getSelectionModel().selectedItemProperty().addListener((observable, before, now) -> { if (!replacing) { renderProfile(now); refresh(); } });
        pending.getSelectionModel().selectedItemProperty().addListener((observable, before, now) -> controls());
        pendingWithdrawals.setId("server-pending-withdrawals"); userWithdrawals.setId("server-user-withdrawals");
        pendingWithdrawals.getSelectionModel().selectedItemProperty().addListener((observable, before, now) -> controls());
        approveWithdrawal.setId("server-approve-withdrawal"); rejectWithdrawal.setId("server-reject-withdrawal");
        withdrawalRejectionReason.setId("server-withdrawal-rejection-reason");
        approveWithdrawal.getStyleClass().add("teal"); rejectWithdrawal.getStyleClass().add("danger");
        approveWithdrawal.setOnAction(event -> reviewWithdrawal(true)); rejectWithdrawal.setOnAction(event -> reviewWithdrawal(false));
        approve.getStyleClass().add("teal"); reject.getStyleClass().add("danger");
        approve.setOnAction(event -> review(true)); reject.setOnAction(event -> review(false));
        TabPane history = new TabPane(new Tab("Lịch sử nạp", userRequests), new Tab("Lịch sử rút", userWithdrawals),
                new Tab("Biến động ví / thanh toán", entries));
        history.setId("server-wallet-history-tabs");
        history.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        history.getStyleClass().addAll("admin-tabs", "admin-history-tabs");
        history.setMinHeight(150); VBox.setVgrow(history, Priority.ALWAYS);
        profile.setWrapText(true); profile.getStyleClass().add("admin-profile");
        profile.setMinHeight(Region.USE_PREF_SIZE);
        profile.setMaxWidth(Double.MAX_VALUE);
        VBox accountList = new VBox(10, DesktopTheme.label("Danh sách tài khoản", "section-title"), accounts);
        accountList.getStyleClass().addAll("card", "admin-account-card");
        accountList.setMinHeight(140); VBox.setVgrow(accounts, Priority.ALWAYS);
        VBox details = new VBox(10, profile, history);
        details.getStyleClass().addAll("card", "admin-account-card");
        SplitPane accountSplit = new SplitPane(accountList, details);
        accountSplit.setOrientation(Orientation.VERTICAL); accountSplit.setDividerPositions(0.44);
        accountSplit.getStyleClass().add("admin-account-split");
        ScrollPane accountScroll = new ScrollPane(accountSplit);
        accountScroll.setFitToWidth(true); accountScroll.setFitToHeight(true);
        accountScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox accountContent = new VBox(accountScroll);
        accountContent.getStyleClass().add("admin-account-content");
        VBox.setVgrow(accountScroll, Priority.ALWAYS);
        rejectionReason.setPromptText("Lý do từ chối"); withdrawalRejectionReason.setPromptText("Lý do từ chối");
        VBox pendingContent = new VBox(14, DesktopTheme.label("Yêu cầu nạp · Mô phỏng", "section-title"),
                pending, new Label("Lý do từ chối"), rejectionReason, new HBox(10, approve, reject));
        pendingContent.getStyleClass().add("admin-review"); pending.setMinHeight(80); VBox.setVgrow(pending, Priority.ALWAYS);
        Tab accountTab = new Tab("T\u00e0i kho\u1ea3n & giao d\u1ecbch", accountContent); accountTab.setId("server-accounts-tab");
        Tab pendingTab = new Tab("Duy\u1ec7t n\u1ea1p demo", pendingContent); accountTab.setClosable(false); pendingTab.setClosable(false);
        pendingTab.setId("server-topup-tab");
        VBox withdrawalContent = new VBox(14, DesktopTheme.label("Yêu cầu rút · Mô phỏng", "section-title"),
                pendingWithdrawals, new Label("Lý do từ chối"), withdrawalRejectionReason, new HBox(10, approveWithdrawal, rejectWithdrawal));
        withdrawalContent.getStyleClass().add("admin-review"); pendingWithdrawals.setMinHeight(80); VBox.setVgrow(pendingWithdrawals, Priority.ALWAYS);
        Tab withdrawalTab = new Tab("Duyệt rút tiền", withdrawalContent); withdrawalTab.setId("server-withdrawal-tab"); withdrawalTab.setClosable(false);
        TabPane tabs = new TabPane(accountTab, pendingTab, withdrawalTab); setCenter(tabs);
        tabs.setId("server-wallet-tabs"); tabs.getStyleClass().addAll("admin-tabs", "admin-subtabs");
        tabs.setMinSize(0, 0);
        accountTab.setText("Tài khoản"); pendingTab.setText("Duyệt nạp"); withdrawalTab.setText("Duyệt rút");
        for (TableView<?> queue : java.util.List.of(pending, pendingWithdrawals)) {
            queue.getColumns().get(2).setVisible(false);
            queue.getColumns().get(5).setVisible(false);
            queue.getColumns().get(4).setPrefWidth(230);
            queue.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        }
        Button refresh = new Button("L\u00e0m m\u1edbi"); refresh.setOnAction(event -> refresh());
        refresh.setId("server-wallet-refresh"); pendingCount.setWrapText(true); pendingCount.setMinWidth(0);
        refresh.setGraphic(ServerIcons.icon("refresh"));
        HBox heading = new HBox(14, pendingCount, DesktopTheme.spacer(), refresh);
        heading.getStyleClass().add("admin-page-heading"); setTop(heading);
        message.getStyleClass().add("admin-status"); message.setWrapText(true); setBottom(message);
        message.visibleProperty().bind(message.textProperty().isNotEmpty()); message.managedProperty().bind(message.visibleProperty());
        poll.setCycleCount(Timeline.INDEFINITE); controls();
    }
    public void show() {
        if (stage == null) { stage = createWindow("Quản lý tài khoản & ví demo", 1140, 800); }
        stage.show(); activate();
    }
    @Override protected void onActivate() { poll.play(); controls(); refresh(); }
    @Override protected void onDeactivate() { poll.stop(); refreshGeneration++; loading = false; controls(); }
    private void renderProfile(WalletAccount value) {
        profile.setText(value == null ? "Ch\u1ecdn t\u00e0i kho\u1ea3n \u0111\u1ec3 xem chi ti\u1ebft."
                : "#" + value.userId() + "  " + value.username() + " | " + value.displayName()
                + "\nEmail: " + empty(value.email()) + " | \u0110i\u1ec7n tho\u1ea1i: " + empty(value.phone()));
        profile.setTooltip(value == null ? null : new Tooltip("Tạo: " + WalletUi.time(value.createdAt())
                + "\nĐăng nhập: " + WalletUi.time(value.lastLoginAt())));
    }
    private String empty(String text) { return text == null || text.isBlank() ? "-" : text; }
    private void refresh() {
        ServerApplication running = server.get();
        if (!isActive() || running == null || loading || reviewing) { return; }
        WalletAccount selected = accounts.getSelectionModel().getSelectedItem(); long selectedId = selected == null ? 0 : selected.userId();
        loading = true;
        long generation = ++refreshGeneration;
        CompletableFuture.supplyAsync(() -> {
            var wallets = running.wallets();
            return new Snapshot(wallets.accounts(), wallets.requests(0, true), selectedId == 0 ? java.util.List.of() : wallets.entries(selectedId),
                    selectedId == 0 ? java.util.List.of() : wallets.requests(selectedId, false), wallets.withdrawals(0, true),
                    selectedId == 0 ? java.util.List.of() : wallets.withdrawals(selectedId, false));
        }, worker).whenComplete((snapshot, error) -> Platform.runLater(() -> {
            if (!isActive() || server.get() != running || generation != refreshGeneration) { return; }
            loading = false;
            WalletAccount current = accounts.getSelectionModel().getSelectedItem();
            if ((current == null ? 0 : current.userId()) != selectedId) { refresh(); return; }
            if (error != null) { message.setText(root(error)); return; }
            replacing = true;
            try {
                if (!accounts.getItems().equals(snapshot.accounts())) { accounts.getItems().setAll(snapshot.accounts()); }
                accounts.getItems().stream().filter(value -> value.userId() == selectedId).findFirst().ifPresent(value -> accounts.getSelectionModel().select(value));
                TopUpRequest chosen = pending.getSelectionModel().getSelectedItem();
                if (!pending.getItems().equals(snapshot.pending())) { pending.getItems().setAll(snapshot.pending()); }
                if (chosen != null) { pending.getItems().stream().filter(value -> value.id().equals(chosen.id())).findFirst().ifPresent(value -> pending.getSelectionModel().select(value)); }
                WithdrawalRequest chosenWithdrawal = pendingWithdrawals.getSelectionModel().getSelectedItem();
                if (!pendingWithdrawals.getItems().equals(snapshot.pendingWithdrawals())) { pendingWithdrawals.getItems().setAll(snapshot.pendingWithdrawals()); }
                if (chosenWithdrawal != null) { pendingWithdrawals.getItems().stream().filter(value -> value.id().equals(chosenWithdrawal.id())).findFirst().ifPresent(value -> pendingWithdrawals.getSelectionModel().select(value)); }
                WalletAccount now = accounts.getSelectionModel().getSelectedItem(); renderProfile(now);
                if (!entries.getItems().equals(snapshot.entries())) { entries.getItems().setAll(snapshot.entries()); }
                if (!userRequests.getItems().equals(snapshot.requests())) { userRequests.getItems().setAll(snapshot.requests()); }
                if (!userWithdrawals.getItems().equals(snapshot.withdrawals())) { userWithdrawals.getItems().setAll(snapshot.withdrawals()); }
                pendingCount.setText(snapshot.pending().size() + " nạp · " + snapshot.pendingWithdrawals().size() + " rút chờ duyệt");
            } finally { replacing = false; controls(); }
        }));
    }
    private void review(boolean accepted) {
        TopUpRequest request = pending.getSelectionModel().getSelectedItem(); ServerApplication running = server.get();
        if (request == null || running == null || reviewing || !isActive()) { return; }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                (accepted ? "C\u1ed9ng " : "T\u1eeb ch\u1ed1i ") + WalletUi.money(request.amount()) + " cho " + request.username() + "?\nY\u00eau c\u1ea7u: " + request.id(), ButtonType.YES, ButtonType.NO);
        confirmation.setHeaderText("X\u00e1c nh\u1eadn n\u1ea1p ti\u1ec1n GI\u1ea2 L\u1eacP"); DesktopTheme.dialog(confirmation, dialogOwner());
        if (confirmation.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) { return; }
        reviewing = true; refreshGeneration++; loading = false; controls();
        String reason = rejectionReason.getText();
        CompletableFuture.supplyAsync(() -> running.wallets().review(request.id(), accepted, reason), worker).whenComplete((result, error) -> Platform.runLater(() -> {
            reviewing = false; if (!isActive() || server.get() != running) { return; }
            message.setText(error == null ? result.username() + ": " + WalletUi.status(result.status()) : root(error)); controls(); refresh();
        }));
    }
    private void reviewWithdrawal(boolean accepted) {
        WithdrawalRequest request = pendingWithdrawals.getSelectionModel().getSelectedItem(); ServerApplication running = server.get();
        if (request == null || running == null || reviewing || !isActive()) { return; }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                (accepted ? "Trừ " : "Từ chối rút ") + WalletUi.money(request.amount()) + " từ ví " + request.username() + "?\nYêu cầu: " + request.id(), ButtonType.YES, ButtonType.NO);
        confirmation.setHeaderText("Xác nhận rút tiền GIẢ LẬP"); DesktopTheme.dialog(confirmation, dialogOwner());
        if (confirmation.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) { return; }
        reviewing = true; refreshGeneration++; loading = false; controls();
        String reason = withdrawalRejectionReason.getText();
        CompletableFuture.supplyAsync(() -> running.wallets().reviewWithdrawal(request.id(), accepted, reason), worker).whenComplete((result, error) -> Platform.runLater(() -> {
            reviewing = false; if (!isActive() || server.get() != running) { return; }
            message.setText(error == null ? result.username() + ": " + WalletUi.withdrawalStatus(result.status()) : root(error)); controls(); refresh();
        }));
    }
    private void controls() {
        boolean disabled = !isActive() || server.get() == null || reviewing || pending.getSelectionModel().getSelectedItem() == null; approve.setDisable(disabled); reject.setDisable(disabled);
        boolean withdrawalDisabled = !isActive() || server.get() == null || reviewing || pendingWithdrawals.getSelectionModel().getSelectedItem() == null;
        approveWithdrawal.setDisable(withdrawalDisabled); rejectWithdrawal.setDisable(withdrawalDisabled);
    }
    private String root(Throwable error) { while (error.getCause() != null) { error = error.getCause(); } return error.getMessage(); }
    private record Snapshot(java.util.List<WalletAccount> accounts, java.util.List<TopUpRequest> pending,
                            java.util.List<WalletEntry> entries, java.util.List<TopUpRequest> requests,
                            java.util.List<WithdrawalRequest> pendingWithdrawals, java.util.List<WithdrawalRequest> withdrawals) { }
}
