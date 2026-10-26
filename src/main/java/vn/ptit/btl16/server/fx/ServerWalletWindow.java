package vn.ptit.btl16.server.fx;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
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

public final class ServerWalletWindow extends BorderPane {
    private final Supplier<ServerApplication> server;
    private final Executor worker;
    private final Stage stage = new Stage();
    private final TableView<WalletAccount> accounts = new TableView<>();
    private final TableView<TopUpRequest> pending = WalletUi.requests(true);
    private final TableView<WalletEntry> entries = WalletUi.ledger();
    private final TableView<TopUpRequest> userRequests = WalletUi.requests(false);
    private final Label profile = DesktopTheme.label("Ch\u1ecdn t\u00e0i kho\u1ea3n \u0111\u1ec3 xem chi ti\u1ebft.", "muted");
    private final Label message = DesktopTheme.label("", "muted");
    private final Label pendingCount = DesktopTheme.label("", "pill");
    private final Button approve = new Button("Ch\u1ea5p nh\u1eadn & c\u1ed9ng ti\u1ec1n");
    private final Button reject = new Button("T\u1eeb ch\u1ed1i");
    private final TextField rejectionReason = new TextField("Yeu cau nap mo phong chua duoc xac nhan");
    private final Timeline poll = new Timeline(new KeyFrame(Duration.seconds(3), event -> refresh()));
    private boolean loading, reviewing, replacing;

    public ServerWalletWindow(Window owner, Supplier<ServerApplication> server, Executor worker) {
        this.server = server; this.worker = worker; stage.initOwner(owner); stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Qu\u1ea3n l\u00fd t\u00e0i kho\u1ea3n & v\u00ed demo");
        WalletUi.column(accounts, "ID", value -> Long.toString(value.userId()), 65);
        WalletUi.column(accounts, "T\u00c0I KHO\u1ea2N", WalletAccount::username, 140);
        WalletUi.column(accounts, "H\u1ecc T\u00caN", WalletAccount::displayName, 170);
        WalletUi.column(accounts, "S\u1ed0 D\u01af", value -> WalletUi.money(value.balance()), 160);
        WalletUi.column(accounts, "GI\u1eee CH\u1ed6", value -> WalletUi.money(value.reserved()), 160);
        WalletUi.column(accounts, "KH\u1ea2 D\u1ee4NG", value -> WalletUi.money(value.available()), 160);
        accounts.setPrefHeight(230); accounts.setMinHeight(140);
        accounts.getSelectionModel().selectedItemProperty().addListener((observable, before, now) -> { if (!replacing) { renderProfile(now); refresh(); } });
        pending.getSelectionModel().selectedItemProperty().addListener((observable, before, now) -> controls());
        approve.getStyleClass().add("teal"); reject.getStyleClass().add("danger");
        approve.setOnAction(event -> review(true)); reject.setOnAction(event -> review(false));
        VBox accountContent = new VBox(12, accounts, profile, DesktopTheme.label("L\u1ecbch s\u1eed n\u1ea1p", "section-title"), userRequests,
                DesktopTheme.label("Bi\u1ebfn \u0111\u1ed9ng v\u00ed / thanh to\u00e1n", "section-title"), entries);
        accountContent.setPadding(new Insets(16));
        ScrollPane accountScroll = new ScrollPane(accountContent); accountScroll.setFitToWidth(true);
        VBox pendingContent = new VBox(14, DesktopTheme.label("Ch\u1ec9 duy\u1ec7t y\u00eau c\u1ea7u demo \u0111\u00e3 g\u1eedi t\u1eeb client", "section-title"),
                DesktopTheme.label("S\u1ed1 ti\u1ec1n \u0111\u01b0\u1ee3c c\u1ed9ng \u0111\u00fang theo y\u00eau c\u1ea7u, kh\u00f4ng ch\u1ec9nh s\u1eeda. Duy\u1ec7t l\u1ea1i kh\u00f4ng c\u1ed9ng tr\u00f9ng. T\u1eeb ch\u1ed1i kh\u00f4ng l\u00e0m thay \u0111\u1ed5i s\u1ed1 d\u01b0.", "muted"),
                pending, new Label("Ly do tu choi (1-300 ky tu)"), rejectionReason, new HBox(10, approve, reject));
        pendingContent.setPadding(new Insets(18)); VBox.setVgrow(pending, Priority.ALWAYS);
        Tab accountTab = new Tab("T\u00e0i kho\u1ea3n & giao d\u1ecbch", accountScroll);
        Tab pendingTab = new Tab("Duy\u1ec7t n\u1ea1p demo", pendingContent); accountTab.setClosable(false); pendingTab.setClosable(false);
        TabPane tabs = new TabPane(accountTab, pendingTab); setCenter(tabs);
        Button refresh = new Button("L\u00e0m m\u1edbi"); refresh.setOnAction(event -> refresh());
        HBox heading = new HBox(14, DesktopTheme.label("T\u00e0i kho\u1ea3n & v\u00ed", "page-title"), DesktopTheme.spacer(), pendingCount, refresh);
        heading.setPadding(new Insets(20)); setTop(heading); message.setPadding(new Insets(12, 20, 16, 20)); setBottom(message);
        Scene scene = new Scene(this, 1120, 760); DesktopTheme.apply(scene); stage.setScene(scene); stage.setMinWidth(900); stage.setMinHeight(540);
        stage.setOnHidden(event -> poll.stop()); poll.setCycleCount(Timeline.INDEFINITE); controls();
    }
    public void show() { DesktopTheme.fitOnScreen(stage, 1140, 800); stage.show(); poll.play(); refresh(); }
    private void renderProfile(WalletAccount value) {
        profile.setText(value == null ? "Ch\u1ecdn t\u00e0i kho\u1ea3n \u0111\u1ec3 xem chi ti\u1ebft."
                : "#" + value.userId() + "  " + value.username() + " | " + value.displayName()
                + "\nEmail: " + empty(value.email()) + " | \u0110i\u1ec7n tho\u1ea1i: " + empty(value.phone())
                + "\nT\u1ea1o: " + WalletUi.time(value.createdAt()) + " | \u0110\u0103ng nh\u1eadp: " + WalletUi.time(value.lastLoginAt())
                + "\nS\u1ed1 d\u01b0: " + WalletUi.money(value.balance()) + " | Gi\u1eef ch\u1ed7: " + WalletUi.money(value.reserved()) + " | Kh\u1ea3 d\u1ee5ng: " + WalletUi.money(value.available()));
    }
    private String empty(String text) { return text == null || text.isBlank() ? "-" : text; }
    private void refresh() {
        ServerApplication running = server.get();
        if (!stage.isShowing() || running == null || loading || reviewing) { return; }
        WalletAccount selected = accounts.getSelectionModel().getSelectedItem(); long selectedId = selected == null ? 0 : selected.userId();
        loading = true;
        CompletableFuture.supplyAsync(() -> {
            var wallets = running.wallets();
            return new Snapshot(wallets.accounts(), wallets.requests(0, true), selectedId == 0 ? java.util.List.of() : wallets.entries(selectedId),
                    selectedId == 0 ? java.util.List.of() : wallets.requests(selectedId, false));
        }, worker).whenComplete((snapshot, error) -> Platform.runLater(() -> {
            loading = false; if (!stage.isShowing() || server.get() != running) { return; }
            if (error != null) { message.setText(root(error)); return; }
            replacing = true;
            try {
                accounts.getItems().setAll(snapshot.accounts());
                accounts.getItems().stream().filter(value -> value.userId() == selectedId).findFirst().ifPresent(value -> accounts.getSelectionModel().select(value));
                TopUpRequest chosen = pending.getSelectionModel().getSelectedItem();
                pending.getItems().setAll(snapshot.pending());
                if (chosen != null) { pending.getItems().stream().filter(value -> value.id().equals(chosen.id())).findFirst().ifPresent(value -> pending.getSelectionModel().select(value)); }
                WalletAccount now = accounts.getSelectionModel().getSelectedItem(); renderProfile(now);
                entries.getItems().setAll(snapshot.entries()); userRequests.getItems().setAll(snapshot.requests());
                pendingCount.setText(snapshot.pending().size() + " y\u00eau c\u1ea7u ch\u1edd duy\u1ec7t");
            } finally { replacing = false; controls(); }
        }));
    }
    private void review(boolean accepted) {
        TopUpRequest request = pending.getSelectionModel().getSelectedItem(); ServerApplication running = server.get();
        if (request == null || running == null || reviewing) { return; }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                (accepted ? "C\u1ed9ng " : "T\u1eeb ch\u1ed1i ") + WalletUi.money(request.amount()) + " cho " + request.username() + "?\nY\u00eau c\u1ea7u: " + request.id(), ButtonType.YES, ButtonType.NO);
        confirmation.setHeaderText("X\u00e1c nh\u1eadn n\u1ea1p ti\u1ec1n GI\u1ea2 L\u1eacP"); DesktopTheme.dialog(confirmation, stage);
        if (confirmation.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) { return; }
        reviewing = true; controls();
        String reason = rejectionReason.getText();
        CompletableFuture.supplyAsync(() -> running.wallets().review(request.id(), accepted, reason), worker).whenComplete((result, error) -> Platform.runLater(() -> {
            reviewing = false; if (!stage.isShowing()) { return; }
            message.setText(error == null ? result.username() + ": " + WalletUi.status(result.status()) : root(error)); controls(); refresh();
        }));
    }
    private void controls() { boolean disabled = reviewing || pending.getSelectionModel().getSelectedItem() == null; approve.setDisable(disabled); reject.setDisable(disabled); }
    private String root(Throwable error) { while (error.getCause() != null) { error = error.getCause(); } return error.getMessage(); }
    private record Snapshot(java.util.List<WalletAccount> accounts, java.util.List<TopUpRequest> pending,
                            java.util.List<WalletEntry> entries, java.util.List<TopUpRequest> requests) { }
}
