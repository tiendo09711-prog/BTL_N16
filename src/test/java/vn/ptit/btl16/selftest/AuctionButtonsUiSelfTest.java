package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.ptit.btl16.client.fx.FxClientController;
import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.model.ClientAuction;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.server.ServerApplication;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class AuctionButtonsUiSelfTest {
    private AuctionButtonsUiSelfTest() { }
    private record UserUi(Stage stage, FxClientController controller, ClientAppModel model, WebSocketClientTransport transport) { }

    public static void main(String[] args) throws Exception {
        Path data = Files.createTempDirectory("btl16-buttons-ui-");
        Path config = data.resolve("server.properties");
        Files.writeString(config, "server.tcp.enabled=false\nserver.websocket.port=0\nmedia.port=0\n"
                + "db.driver=org.h2.Driver\ndb.name=auction\ndb.user=sa\ndb.password=\nsecurity.passwordIterations=10000\n");
        System.setProperty("btl16.server.config", config.toString()); System.setProperty("btl16.dataDir", data.toString());
        System.setProperty("btl16.client.manualConnect", "true");
        Platform.startup(() -> Platform.setImplicitExit(false));
        ArrayList<UserUi> users = new ArrayList<>();
        ServerApplication server = ServerApplication.create(ServerConfig.loadDefault());
        try {
            server.start(); String endpoint = "ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws";
            UserUi host = open(endpoint); users.add(host);
            press(host, "registerButton");
            DialogPane register = dialog("\u0110\u0103ng k\u00fd t\u00e0i kho\u1ea3n");
            fx(() -> { textRow(register, 0).setText("buttons_host"); textRow(register, 1).setText("Password123!");
                textRow(register, 2).setText("Password123!"); textRow(register, 3).setText("Buttons Host"); submit(register); return null; });
            DialogPane notice = dialog(null); fx(() -> { submit(notice); return null; });
            login(host, "buttons_host");
            AuctionApi hostApi = new AuctionApi(host.transport());
            long product = Long.parseLong(ok(TestMedia.create(hostApi, "BUTTON_ITEM", "Button camera", TestMedia.DESCRIPTION)).get("productId"));
            pressText(host, "T\u1ea1o ph\u00f2ng \u0111\u1ea5u gi\u00e1");
            DialogPane create = dialog("T\u1ea1o ph\u00f2ng \u0111\u1ea5u gi\u00e1");
            fx(() -> { textRow(create, 1).setText("100"); textRow(create, 2).setText("10"); submit(create); return null; });
            DialogPane review = dialog(null);
            TestSupport.equals(0, host.model().auctionSnapshot().size(), "review does not create before confirmation");
            fx(() -> { ((Button) review.lookupButton(ButtonType.YES)).fire(); return null; });
            waitFor(() -> host.model().auctionSnapshot().size() == 1, "create-room button publishes a room");
            long room = fx(() -> host.model().auctionSnapshot().get(0).getAuctionId()); select(host, room);
            press(host, "joinButton"); waitFor(() -> host.model().getJoinedAuctionId() != null, "host joins to receive live room events");
            waitFor(() -> !button(host, "extendButton").isDisabled(), "host controls wait for room and wallet sync");
            long beforeExtension = fx(() -> host.model().auctionSnapshot().get(0).getVersion());
            press(host, "extendButton"); DialogPane extend = dialog("Gia h\u1ea1n");
            fx(() -> { ((TextInputControl) extend.lookup(".text-field")).setText("10"); submit(extend); return null; });
            waitFor(() -> host.model().auctionSnapshot().get(0).getVersion() > beforeExtension, "extend button updates authoritative state");
            try (var watcher = new WebSocketClientTransport(endpoint, 2097152, 5000, 10000)) {
                watcher.connect().get(10, TimeUnit.SECONDS); AccountApi account = new AccountApi(watcher);
                ok(account.register("buttons_watcher", "Password123!", "Watcher", "", "")); ok(account.login("buttons_watcher", "Password123!"));
                AuctionApi api = new AuctionApi(watcher); ok(api.join(room));
                var kicked = new java.util.concurrent.CountDownLatch(1);
                watcher.addEventListener(event -> { if (event.getType() == vn.ptit.btl16.common.protocol.MessageType.AUCTION_KICKED) { kicked.countDown(); } });
                press(host, "kickButton"); DialogPane kick = dialog("Kick user");
                fx(() -> { ((TextInputControl) kick.lookup(".text-field")).setText("buttons_watcher"); submit(kick); return null; });
                TestSupport.check(kicked.await(10, TimeUnit.SECONDS), "kick button sends a real event to a non-bidder");
                ApiResponse denied = api.join(room).get(10, TimeUnit.SECONDS);
                TestSupport.equals("USER_BLOCKED_FROM_AUCTION", denied.getErrorCode(), "kicked watcher cannot rejoin");
            }
            UserUi buyer = open(endpoint); users.add(buyer);
            AccountApi buyerAccount = new AccountApi(buyer.transport());
            ok(buyerAccount.register("buttons_buyer", "Password123!", "Buttons Buyer", "", "")); login(buyer, "buttons_buyer");
            TestMedia.fundWallets(server.wallets());
            pressText(buyer, "L\u00e0m m\u1edbi"); waitFor(() -> buyer.model().auctionSnapshot().size() == 1, "refresh button loads rooms"); select(buyer, room);
            fx(() -> { ((TextField) field(buyer.controller(), "searchField")).setText("Button camera"); return null; });
            pressText(buyer, "T\u00ecm ki\u1ebfm"); waitFor(() -> table(buyer).getItems().size() == 1, "search button");
            pressText(buyer, "X\u00f3a l\u1ecdc");
            TestSupport.check(fx(() -> button(buyer, "bidButton").isDisabled()), "cannot bid before joining");
            press(buyer, "joinButton"); waitFor(() -> buyer.model().getJoinedAuctionId() != null, "join button");
            waitFor(() -> !button(buyer, "bidButton").isDisabled(), "bid waits for room and private wallet sync");
            fx(() -> { ((TextField) field(buyer.controller(), "bidField")).setText("110"); button(buyer, "bidButton").fire(); return null; });
            waitFor(() -> buyer.model().joinedAuction().getCurrentWinnerId() != null, "bid button sends actual bid");
            waitFor(() -> ((Label) field(buyer.controller(), "bidFeedback")).getText().contains("ch\u1ea5p nh\u1eadn"), "accepted bid is acknowledged inline");
            waitFor(() -> button(buyer, "bidButton").isDisabled(), "leader cannot self-raise in UI");
            waitFor(() -> button(host, "endButton").isDisabled() && button(host, "cancelButton").isDisabled()
                    && button(host, "extendButton").isDisabled(), "host mutations disabled after accepted bid");
            press(buyer, "historyButton"); DialogPane history = dialog("L\u1ecbch s\u1eed bid ph\u00f2ng #" + room);
            waitFor(() -> ((TableView<?>) history.lookup("#bid-history-table")).getItems().toString().contains("buttons_buyer"), "history table loads real server bid");
            fx(() -> { TestSupport.equals(4, ((TableView<?>) history.lookup("#bid-history-table")).getColumns().size(), "history has time, bidder, amount and increase"); ((Button) history.lookupButton(ButtonType.CLOSE)).fire(); return null; });
            try (var rival = new WebSocketClientTransport(endpoint, 2097152, 5000, 10000)) {
                rival.connect().get(10, TimeUnit.SECONDS); AccountApi account = new AccountApi(rival);
                ok(account.register("buttons_rival", "Password123!", "Rival", "", "")); ok(account.login("buttons_rival", "Password123!"));
                TestMedia.fundWallets(server.wallets());
                AuctionApi api = new AuctionApi(rival); ok(api.join(room)); ok(api.bid(room, new BigDecimal("120")));
                waitFor(() -> !button(buyer, "bidButton").isDisabled(), "outbid buyer can compete again");
            }
            press(buyer, "leaveButton"); waitFor(() -> buyer.model().getJoinedAuctionId() == null, "leave button");
            pressText(buyer, "T\u00e0i kho\u1ea3n"); DialogPane profile = dialog("H\u1ed3 s\u01a1 t\u00e0i kho\u1ea3n");
            fx(() -> { textRow(profile, 1).setText("Updated Buyer"); submit(profile); return null; });
            waitFor(() -> buyer.model().getDisplayName().equals("Updated Buyer"), "profile save button persists identity");
            pressText(buyer, "T\u00e0i kho\u1ea3n"); DialogPane passwordProfile = dialog("H\u1ed3 s\u01a1 t\u00e0i kho\u1ea3n");
            Platform.runLater(() -> { ButtonType change = passwordProfile.getButtonTypes().stream().filter(value -> value.getButtonData() == ButtonBar.ButtonData.OTHER).findFirst().orElseThrow();
                ((Button) passwordProfile.lookupButton(change)).fire(); });
            DialogPane password = dialog("\u0110\u1ed5i m\u1eadt kh\u1ea9u");
            fx(() -> { textRow(password, 0).setText("Password123!"); textRow(password, 1).setText("NewPassword456!");
                textRow(password, 2).setText("NewPassword456!"); submit(password); return null; });
            DialogPane changed = dialog(null); fx(() -> { submit(changed); return null; });
            pressText(buyer, "\u0110\u0103ng xu\u1ea5t"); waitFor(() -> !buyer.model().hasSessionToken(), "logout button clears identity");
            TestSupport.equals("INVALID_CREDENTIALS", buyerAccount.login("buttons_buyer", "Password123!").get(10, TimeUnit.SECONDS).getErrorCode(), "old password no longer works");
            fx(() -> { ((TextField) field(buyer.controller(), "usernameField")).setText("buttons_buyer");
                ((TextInputControl) field(buyer.controller(), "passwordField")).setText("NewPassword456!"); button(buyer, "loginButton").fire(); return null; });
            waitFor(() -> buyer.model().hasSessionToken(), "new password authenticates through login button");
            press(host, "leaveButton"); waitFor(() -> host.model().getJoinedAuctionId() == null, "host leaves focused room to browse marketplace");
            for (boolean cancel : new boolean[]{true, false}) {
                long anotherProduct = Long.parseLong(ok(TestMedia.create(hostApi, cancel ? "BUTTON_CANCEL" : "BUTTON_END", "Empty room", TestMedia.DESCRIPTION)).get("productId"));
                long anotherRoom = Long.parseLong(ok(hostApi.createAuction(anotherProduct, new BigDecimal("100"), new BigDecimal("10"), 1)).get("auctionId"));
                pressText(host, "L\u00e0m m\u1edbi"); waitFor(() -> host.model().auctionSnapshot().stream().anyMatch(value -> value.getAuctionId() == anotherRoom), "host sees new empty room");
                select(host, anotherRoom); press(host, "joinButton");
                waitFor(() -> java.util.Objects.equals(host.model().getJoinedAuctionId(), anotherRoom), "host joins empty room before managing");
                press(host, cancel ? "cancelButton" : "endButton"); DialogPane confirmation = dialog(null);
                fx(() -> { ((Button) confirmation.lookupButton(ButtonType.YES)).fire(); return null; });
                waitFor(() -> host.model().auctionSnapshot().stream().anyMatch(value -> value.getAuctionId() == anotherRoom && !value.isOpen()), "cancel/end button closes only empty room");
                press(host, "leaveButton"); waitFor(() -> host.model().getJoinedAuctionId() == null, "host leaves closed room to browse next room");
            }
            TestSupport.check(product > 0, "seller product remains in isolated test database");
            waitFor(() -> ((java.util.Set<?>) field(host.controller(), "imageRequests")).isEmpty()
                    && (((javafx.scene.image.ImageView) field(host.controller(), "productImage")).getImage() == null
                    || ((javafx.scene.image.ImageView) field(host.controller(), "productImage")).getImage().getProgress() == 1), "last thumbnail finishes before server teardown");
            System.out.println("[PASS] AuctionButtonsUiSelfTest: actual connect/register/login/create/extend/kick/search/clear/join/bid/history/leave/profile/password/logout/cancel/end buttons, leader and host-rule states");
        } finally {
            fx(() -> { for (UserUi user : users) { user.controller().close(); user.stage().hide(); }
                new ArrayList<>(Window.getWindows()).forEach(Window::hide); return null; }); server.close(); Platform.exit();
        }
    }

    private static UserUi open(String endpoint) throws Exception {
        UserUi user = fx(() -> { Stage stage = new Stage(); var transport = new WebSocketClientTransport(endpoint, 2097152, 5000, 10000);
            var model = new ClientAppModel(); var controller = new FxClientController(stage, ClientConfig.loadDefault(), model, transport,
                    new AccountApi(transport), new AuctionApi(transport)); controller.start(); return new UserUi(stage, controller, model, transport); });
        fx(() -> { ((TextField) field(user.controller(), "endpointField")).setText(endpoint); button(user, "connectButton").fire(); return null; });
        waitFor(() -> user.transport().isConnected() && !button(user, "registerButton").isDisabled()
                && !button(user, "loginButton").isDisabled(), "connect button establishes WebSocket and UI is ready"); return user;
    }
    private static void login(UserUi user, String username) throws Exception {
        fx(() -> { ((TextField) field(user.controller(), "usernameField")).setText(username);
            ((TextInputControl) field(user.controller(), "passwordField")).setText("Password123!"); button(user, "loginButton").fire(); return null; });
        waitFor(() -> user.model().hasSessionToken(), "login button authenticates " + username);
    }
    private static void press(UserUi user, String name) throws Exception {
        Button button = fx(() -> button(user, name)); waitFor(() -> !button.isDisabled(), "button is enabled: " + name); Platform.runLater(button::fire);
    }
    private static void pressText(UserUi user, String text) throws Exception {
        Button button = fx(() -> user.stage().getScene().getRoot().lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(value -> value.getText().endsWith(text)).findFirst().orElseThrow(() -> new AssertionError("Missing button: " + text)));
        Platform.runLater(button::fire);
    }
    private static DialogPane dialog(String title) throws Exception {
        CompletableFuture<DialogPane> found = new CompletableFuture<>();
        waitFor(() -> { for (Window window : new ArrayList<>(Window.getWindows())) {
            if (!window.isShowing() || !(window instanceof Stage stage) || (title != null && !title.equals(stage.getTitle()))) { continue; }
            Node node = window.getScene().getRoot().lookup(".dialog-pane");
            if (node instanceof DialogPane pane) { found.complete(pane); return true; }
        } return false; }, "dialog: " + title); return found.get();
    }
    private static TextInputControl textRow(DialogPane dialog, int row) {
        return (TextInputControl) ((GridPane) dialog.getContent()).getChildren().stream().filter(TextInputControl.class::isInstance)
                .filter(node -> java.util.Objects.equals(GridPane.getRowIndex(node), row)).findFirst().orElseThrow();
    }
    private static void submit(DialogPane dialog) {
        ButtonType type = dialog.getButtonTypes().stream().filter(value -> value.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
        ((Button) dialog.lookupButton(type)).fire();
    }
    @SuppressWarnings("unchecked") private static TableView<ClientAuction> table(UserUi user) throws Exception { return (TableView<ClientAuction>) field(user.controller(), "auctionTable"); }
    private static void select(UserUi user, long room) throws Exception { fx(() -> { table(user).getSelectionModel().select(table(user).getItems().stream().filter(value -> value.getAuctionId() == room).findFirst().orElseThrow()); return null; }); }
    private static Button button(UserUi user, String name) throws Exception { return (Button) field(user.controller(), name); }
    private static Object field(Object target, String name) throws Exception { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception { ApiResponse response = future.get(15, TimeUnit.SECONDS); TestSupport.check(response.isSuccess(), response.getErrorCode() + ": " + response.getMessageText()); return response; }
    private static <Result> Result fx(Callable<Result> action) throws Exception { var result = new CompletableFuture<Result>(); Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } }); return result.get(15, TimeUnit.SECONDS); }
    private static void waitFor(Callable<Boolean> condition, String description) throws Exception { long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20); while (System.nanoTime() < deadline) { if (fx(condition)) { return; } Thread.sleep(50); } throw new AssertionError("Timeout: " + description); }
}
