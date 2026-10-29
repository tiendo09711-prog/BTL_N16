package vn.ptit.btl16.selftest;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;
import vn.ptit.btl16.client.fx.*;
import vn.ptit.btl16.client.model.*;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.*;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.server.ServerApplication;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Two live clients: room profile invitation, mutual private chat, unread/minimize and logout cleanup. */
public final class FriendsUiSelfTest {
    public static void main(String[] args) throws Exception {
        String oldConfig = System.getProperty("btl16.server.config"), oldData = System.getProperty("btl16.dataDir");
        Path directory = Files.createTempDirectory("btl16-friends-ui-"), settings = directory.resolve("server.properties");
        Files.writeString(settings, "server.tcp.enabled=false\nserver.websocket.bindAddress=127.0.0.1\nserver.websocket.port=0\nmedia.port=0\n"
                + "db.driver=org.h2.Driver\ndb.name=friends_ui\ndb.user=sa\ndb.password=\nsecurity.passwordIterations=10000\n");
        System.setProperty("btl16.server.config", settings.toString()); System.setProperty("btl16.dataDir", directory.toString());
        Platform.startup(() -> Platform.setImplicitExit(false));
        FxClientController controller = null; FriendsPane bobPane = null; Timeline timer = null;
        try (var server = ServerApplication.create(ServerConfig.loadDefault())) {
            server.start(); String endpoint = "ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws";
            try (var alice = client(endpoint); var bob = client(endpoint)) {
                var aliceIdentity = register(alice, "friend_ui_alice", "Nguyễn Minh Anh");
                var bobIdentity = register(bob, "friend_ui_bob", "Trần Minh Đức");
                var aliceModel = new ClientAppModel(); aliceModel.applyIdentity(aliceIdentity);
                var bobModel = new ClientAppModel(); bobModel.applyIdentity(bobIdentity);
                long aliceId = aliceModel.getUserId(), bobId = bobModel.getUserId();
                controller = fx(() -> {
                    Stage stage = new Stage(); var view = new FxClientController(stage, ClientConfig.loadDefault(), aliceModel, alice, new AccountApi(alice), new AuctionApi(alice));
                    invoke(view, "buildScenes"); stage.setScene((Scene) field(view, "mainScene")); DesktopTheme.fitOnScreen(stage, 1250, 850); stage.show(); return view;
                });
                var alicePane = (FriendsPane) field(controller, "friendsPane");
                bobPane = fx(() -> {
                    Stage stage = new Stage(); var pane = new FriendsPane(bob, bobModel);
                    var page = new BorderPane(new Label("Tài khoản thứ hai: Trần Minh Đức")); page.setTop(new HBox(pane.button()));
                    var root = new StackPane(page, pane); StackPane.setAlignment(pane, Pos.BOTTOM_RIGHT); StackPane.setMargin(pane, new Insets(12));
                    Scene scene = new Scene(root, 620, 700); DesktopTheme.apply(scene); stage.setScene(scene); stage.show(); return pane;
                });
                FriendsPane second = bobPane;
                alice.addEventListener(event -> Platform.runLater(() -> alicePane.onEvent(event)));
                bob.addEventListener(event -> Platform.runLater(() -> second.onEvent(event)));
                timer = fx(() -> { var poll = new Timeline(new KeyFrame(Duration.millis(250), event -> { alicePane.tick(); second.tick(); })); poll.setCycleCount(Timeline.INDEFINITE); poll.play(); return poll; });
                waitFor(() -> !alicePane.button().isDisabled(), "friend controls become available after login");
                Stage aliceStage = (Stage) field(controller, "stage");
                fx(() -> {
                    var bell = aliceStage.getScene().lookup("#marketplace-notification-bell");
                    TestSupport.check(bell.getParent() == alicePane.button().getParent(), "person icon sits beside the notification bell in the same top bar");
                    TestSupport.check(!alicePane.isVisible() && !alicePane.isManaged() && alicePane.lookup("#friends-chat-tab") == null,
                            "login leaves no persistent chat launcher or dock over the sidebar");
                    var logout = (Button) aliceStage.getScene().lookup("#marketplace-logout");
                    TestSupport.check(logout.isVisible() && !logout.isDisabled() && logout.getText().equals("Đăng xuất"),
                            "sidebar retains its available logout action");
                    return null;
                });
                screenshot(aliceStage, "target/friends-sidebar-logout.png");

                var auctions = new AuctionApi(alice);
                long product = Long.parseLong(ok(TestMedia.create(auctions, "", "Sản phẩm kết bạn thử nghiệm", TestMedia.DESCRIPTION)).get("productId"));
                long room = Long.parseLong(ok(auctions.createAuction(product, BigDecimal.valueOf(100), BigDecimal.TEN, 10)).get("auctionId"));
                ok(new AuctionApi(bob).join(room));
                var snapshot = ok(auctions.join(room)); var social = (RoomSocialPane) field(controller, "socialRoom");
                fx(() -> { social.setRoom(ClientWireParser.auction(snapshot.getWireMessage().getData(), ""), aliceId); social.openProfile(bobId); return null; });
                waitFor(() -> windowsNode("#room-profile-friend") != null, "clicking a room member opens public profile with friend action");
                fx(() -> { ((Button) windowsNode("#room-profile-friend")).fire(); return null; });
                waitFor(() -> alicePane.lookup("#friend-add-" + bobId) != null, "room profile opens the add-friend item");
                fx(() -> {
                    ((TextField) alicePane.lookup("#friends-search")).setText("Trần Minh Đức");
                    ((Button) alicePane.lookup("#friends-search-button")).fire();
                    TestSupport.check(((Label) alicePane.lookup("#friends-status")).getText().contains("4 số"), "manual friend search requires a four-digit code");
                    ((TextField) alicePane.lookup("#friends-search-code")).setText("#" + bobModel.getFriendCode());
                    ((Button) alicePane.lookup("#friends-search-button")).fire(); return null;
                });
                waitFor(() -> !((Button) alicePane.lookup("#friends-search-button")).isDisabled()
                        && labels((Parent) field(alicePane, "peopleRows")).contains("#" + bobModel.getFriendCode()), "search displays the correct account discriminator");
                fire(alicePane, "friend-add-" + bobId);
                fx(() -> { second.button().fire(); ((ToggleButton) second.lookup("#friends-invitations-tab")).fire(); return null; });
                waitFor(() -> second.lookup("#friend-accept-" + aliceId) != null, "recipient receives the invitation live");
                screenshot((Stage) second.getScene().getWindow(), "target/friends-invitation.png");
                fire(second, "friend-accept-" + aliceId);
                waitFor(() -> alicePane.lookup("#friend-message-" + bobId) != null, "acceptance enables private chat for the sender");
                screenshot(aliceStage, "target/friends-directory.png");
                fire(alicePane, "friend-message-" + bobId);
                fx(() -> { ((ToggleButton) second.lookup("#friends-list-tab")).fire(); return null; });
                waitFor(() -> second.lookup("#friend-message-" + aliceId) != null, "recipient sees a mutual friendship");
                fire(second, "friend-message-" + aliceId);
                waitFor(() -> !((TextArea) alicePane.lookup("#private-chat-input")).isDisabled(), "private history loads before composing");
                write(alicePane, "Xin chào Đức, mình trao đổi ở đây nhé! 🌷");
                waitFor(() -> labels((Parent) field(second, "messageRows")).contains("Xin chào Đức"), "private message appears in the other account");
                write(second, "Mình đã nhận được tin nhắn!");
                waitFor(() -> labels((Parent) field(alicePane, "messageRows")).contains("Mình đã nhận"), "private messages work in both directions");
                screenshot(aliceStage, "target/friends-private-chat.png");
                fx(() -> {
                    var panel = alicePane.lookup("#friends-panel").localToScene(alicePane.lookup("#friends-panel").getLayoutBounds());
                    double width = aliceStage.getScene().getWidth(), height = aliceStage.getScene().getHeight();
                    TestSupport.check(panel.getMinX() > width - 410 && panel.getMaxY() < height && panel.getMaxY() > height - 25
                                    && panel.getMaxX() > width - 25 && panel.getMaxX() <= width,
                            "chat remains a small dock at the bottom right of the application");
                    return null;
                });
                fire(second, "friends-minimize"); write(alicePane, "Tin mới khi đang thu gọn");
                waitFor(() -> second.button().lookup("#friends-count").isVisible(), "new minimized message displays an unread count");
                TestSupport.check(fx(() -> !second.isVisible() && !second.isManaged() && !second.lookup("#friends-panel").isVisible()),
                        "new messages leave the entire minimized dock hidden");
                fx(() -> { second.button().fire(); ((ToggleButton) second.lookup("#friends-list-tab")).fire(); return null; });
                TestSupport.check(!fx(() -> second.lookup("#private-chat-input").getParent().isVisible()),
                        "people icon opens the directory without opening the private conversation");
                fire(second, "friend-message-" + aliceId);
                waitFor(() -> labels((Parent) field(second, "messageRows")).contains("Tin mới khi đang thu gọn"), "messaging through friends loads the missed message");
                waitFor(() -> !second.button().lookup("#friends-count").isVisible(), "viewing the conversation clears unread state");
                fire(second, "friends-close");
                TestSupport.check(fx(() -> !second.isVisible() && !second.isManaged()), "closing the chat removes the entire dock");
                fx(() -> { second.button().fire(); return null; });
                fire(second, "friend-message-" + aliceId);
                fx(() -> { ((TextArea) second.lookup("#private-chat-input")).setText("Bản nháp"); bobModel.clearIdentity(); second.tick(); return null; });
                TestSupport.check(!fx(second::isVisible) && fx(() -> labels((Parent) field(second, "messageRows"))).isEmpty(), "logout hides and removes private data and drafts");
                fire(alicePane, "friends-close");
                fx(() -> { ((Button) aliceStage.getScene().lookup("#marketplace-logout")).fire(); return null; });
                waitFor(() -> !aliceModel.hasSessionToken(), "sidebar logout still signs the user out");
                TestSupport.check(!fx(alicePane::isVisible), "sidebar logout leaves private chat hidden");
                System.out.println("[PASS] FriendsUiSelfTest: visible working logout, no persistent launcher, friends-only chat opening, two-way messages, unread while closed and session cleanup");
            }
        } finally {
            Timeline poll = timer; FxClientController view = controller; FriendsPane second = bobPane;
            fx(() -> { if (poll != null) { poll.stop(); } if (view != null) { view.close(); } if (second != null) { second.close(); }
                new ArrayList<>(Window.getWindows()).forEach(Window::hide); return null; });
            Platform.exit(); restore("btl16.server.config", oldConfig); restore("btl16.dataDir", oldData);
        }
    }

    private static void write(FriendsPane pane, String body) throws Exception {
        waitFor(() -> !((TextArea) pane.lookup("#private-chat-input")).isDisabled(), "composer ready");
        fx(() -> { ((TextArea) pane.lookup("#private-chat-input")).setText(body); ((Button) pane.lookup("#private-chat-send")).fire(); return null; });
    }
    private static void fire(Parent root, String id) throws Exception { fx(() -> { ((Button) root.lookup("#" + id)).fire(); return null; }); }
    private static Node windowsNode(String selector) { for (Window window : Window.getWindows()) { if (window.isShowing() && window.getScene() != null) { Node node = window.getScene().lookup(selector); if (node != null) { return node; } } } return null; }
    private static String labels(Parent parent) { StringBuilder result = new StringBuilder(); for (Node node : parent.getChildrenUnmodifiable()) { if (node instanceof Label label) { result.append(label.getText()).append('\n'); } if (node instanceof Parent child) { result.append(labels(child)); } } return result.toString(); }
    private static Object field(Object object, String name) throws Exception { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
    private static void invoke(Object object, String name) throws Exception { Method method = object.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(object); }
    private static WebSocketClientTransport client(String endpoint) throws Exception { var client = new WebSocketClientTransport(endpoint, 2097152, 5000, 10000); client.connect().get(10, TimeUnit.SECONDS); return client; }
    private static ApiResponse register(WebSocketClientTransport client, String username, String name) throws Exception { ok(new AccountApi(client).register(username, "Password123!", name, "", "")); return ok(new AccountApi(client).login(username, "Password123!")); }
    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception { var response = future.get(20, TimeUnit.SECONDS); TestSupport.check(response.isSuccess(), response.getErrorCode() + " " + response.getMessageText()); return response; }
    private static <T> T fx(Callable<T> task) throws Exception { var future = new CompletableFuture<T>(); Platform.runLater(() -> { try { future.complete(task.call()); } catch (Throwable error) { future.completeExceptionally(error); } }); return future.get(20, TimeUnit.SECONDS); }
    private static void waitFor(Callable<Boolean> condition, String description) throws Exception { long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(25); while (System.nanoTime() < until) { if (fx(condition)) { return; } Thread.sleep(50); } throw new AssertionError("Timeout: " + description); }
    private static void restore(String key, String value) { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } }
    private static void screenshot(Stage stage, String path) throws Exception {
        BufferedImage bitmap = fx(() -> { var image = stage.getScene().snapshot(null); int width = (int) image.getWidth(), height = (int) image.getHeight();
            var result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) { for (int x = 0; x < width; x++) { result.setRGB(x, y, image.getPixelReader().getArgb(x, y)); } } return result; });
        ImageIO.write(bitmap, "png", Path.of(path).toFile());
    }
}
