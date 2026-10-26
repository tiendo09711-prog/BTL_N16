package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.*;
import vn.ptit.btl16.client.fx.RoomSocialPane;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.network.*;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.account.repository.TestUserRepository;
import vn.ptit.btl16.server.account.security.Pbkdf2PasswordHasher;
import vn.ptit.btl16.server.auction.repository.TestAuctionRepository;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class RoomSocialUiSelfTest {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        var config = ServerConfig.forTests(0, 2, 2, 1); var hasher = new Pbkdf2PasswordHasher(config.getPasswordIterations());
        try (var server = ServerApplication.createForTests(config, TestUserRepository.withDemoUsers(hasher), TestAuctionRepository.withDemoAuctions(config), hasher)) {
            server.start();
            try (var host = client(server.getBoundPort()); var guest = client(server.getBoundPort())) {
                ok(new AccountApi(host).login("demo", "demo123")); ok(new AccountApi(guest).login("alice", "alice123"));
                ok(new RoomSocialApi(guest).saveProfile("Toi thich cac phien dau gia cong bang.", RoomSocialSelfTest.avatar()));
                AuctionApi hostApi = new AuctionApi(host), guestApi = new AuctionApi(guest);
                long normal = room(hostApi, "UI_SOCIAL_NORMAL", false), anonymous = room(hostApi, "UI_SOCIAL_ANON", true);
                ok(hostApi.join(normal)); ApiResponse normalJoin = ok(guestApi.join(normal));
                ok(new RoomSocialApi(host).send(normal, "Chao mung ban den voi phien dau gia!", "ui-welcome-normal"));
                ok(new RoomSocialApi(guest).send(normal, "Minh da san sang, chuc moi nguoi may man!", "ui-guest-normal"));
                TrackingTransport transport = new TrackingTransport(guest);
                RoomSocialPane pane = fx(() -> new RoomSocialPane(transport));
                guest.addEventListener(event -> { if (event.getType() == MessageType.ROOM_CHAT_MESSAGE) { Platform.runLater(() -> pane.onMessage(event.getData())); } });
                Stage stage = fx(() -> {
                    Stage window = new Stage(); VBox root = new VBox(14, DesktopTheme.brand("PHONG DAU GIA"), pane); root.setStyle("-fx-padding: 20;");
                    Scene scene = new Scene(root, 760, 660); DesktopTheme.apply(scene); window.setScene(scene); window.show(); pane.setOwner(window);
                    pane.setRoom(ClientWireParser.auction(normalJoin.getWireMessage().getData(), ""), 2); return window;
                });
                try {
                    await(() -> !field(pane, "loading", Boolean.class) && pane.lookupAll(".social-message").size() == 2, "normal chat history");
                    await(() -> pane.lookupAll(".social-avatar").stream().anyMatch(node -> ((Button) node).getGraphic() instanceof ImageView), "normal avatar loaded");
                    fx(() -> {
                        TestSupport.check(pane.lookupAll(".social-avatar").stream().allMatch(node -> !node.isDisabled()), "normal avatars interactive");
                        ((Button) pane.lookupAll(".social-avatar").stream().filter(node -> ((Button) node).getGraphic() instanceof ImageView).findFirst().orElseThrow()).fire(); return null;
                    });
                    await(() -> Window.getWindows().stream().anyMatch(window -> window instanceof Stage dialog && "H\u1ed3 s\u01a1 c\u00f4ng khai".equals(dialog.getTitle())), "public profile dialog");
                    fx(() -> {
                        Stage dialog = (Stage) Window.getWindows().stream().filter(window -> window instanceof Stage candidate && "H\u1ed3 s\u01a1 c\u00f4ng khai".equals(candidate.getTitle())).findFirst().orElseThrow();
                        TestSupport.check(labels(dialog.getScene()).contains("Alice"), "dialog display name");
                        TestSupport.check(!labels(dialog.getScene()).contains("alice@local.test"), "dialog never exposes email");
                        snapshot(dialog.getScene(), "public-profile"); dialog.close(); snapshot(stage.getScene(), "normal-room");
                        ((TextField) pane.lookup("#room-chat-input")).setText("Tin nhan gui tu giao dien"); ((Button) pane.lookup("#room-chat-send")).fire(); return null;
                    });
                    await(() -> labels(stage.getScene()).contains("Tin nhan gui tu giao dien"), "send from UI");
                    fx(() -> { pane.editOwnProfile(); return null; });
                    String editorTitle = "Avatar v\u00e0 gi\u1edbi thi\u1ec7u c\u00f4ng khai";
                    await(() -> Window.getWindows().stream().anyMatch(window -> window instanceof Stage dialog && editorTitle.equals(dialog.getTitle())), "public profile editor");
                    fx(() -> {
                        Stage editor = (Stage) Window.getWindows().stream().filter(window -> window instanceof Stage dialog && editorTitle.equals(dialog.getTitle())).findFirst().orElseThrow();
                        ((TextArea) editor.getScene().getRoot().lookup(".text-area")).setText("Gioi thieu cap nhat tu giao dien");
                        DialogPane dialog = (DialogPane) editor.getScene().getRoot();
                        ButtonType save = dialog.getButtonTypes().stream().filter(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
                        ((Button) dialog.lookupButton(save)).fire(); return null;
                    });
                    await(() -> Window.getWindows().stream().noneMatch(window -> window instanceof Stage dialog && editorTitle.equals(dialog.getTitle())), "profile saved");
                    TestSupport.equals("Gioi thieu cap nhat tu giao dien", ok(new RoomSocialApi(guest).ownProfile()).get("biography"), "editor persisted public bio");
                    ok(hostApi.join(anonymous)); ApiResponse anonymousJoin = ok(guestApi.join(anonymous));
                    ok(new RoomSocialApi(guest).send(anonymous, "Xin chao ca phong an danh!", "ui-anonymous-001"));
                    await(() -> field(pane, "pendingProfiles", Set.class).isEmpty(), "normal avatar requests finish");
                    int before = transport.profileRequests.get();
                    fx(() -> { pane.setRoom(ClientWireParser.auction(anonymousJoin.getWireMessage().getData(), ""), 2); return null; });
                    await(() -> !field(pane, "loading", Boolean.class) && labels(stage.getScene()).contains("Xin chao ca phong an danh!"), "anonymous history");
                    fx(() -> {
                        TestSupport.check(!labels(stage.getScene()).contains("Alice") && !labels(stage.getScene()).contains("Chao mung ban"), "switch clears old names and history");
                        TestSupport.check(pane.lookupAll(".social-avatar").stream().allMatch(node -> node.isDisabled() && ((Button) node).getText().isBlank() && ((Button) node).getGraphic() == null), "anonymous avatars blank and disabled");
                        pane.openProfile(-1); snapshot(stage.getScene(), "anonymous-room");
                        stage.setWidth(520); stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout(); snapshot(stage.getScene(), "compact-room"); return null;
                    });
                    TestSupport.equals(before, transport.profileRequests.get(), "anonymous room never fetches profiles or avatars");
                    fx(() -> { pane.clear(); TestSupport.check(((TextField) pane.lookup("#room-chat-input")).isDisabled(), "leave disables composer"); return null; });
                } finally { fx(() -> { pane.clear(); stage.close(); return null; }); }
            }
            System.out.println("[PASS] RoomSocialUiSelfTest: actual socket chat, avatar, clickable safe profile, anonymous blank avatars/no profile requests, room reset and compact screenshots");
        } finally { Platform.exit(); }
    }
    private static long room(AuctionApi api, String code, boolean anonymous) throws Exception {
        long product = Long.parseLong(ok(TestMedia.create(api, code, "Phong tuong tac", TestMedia.DESCRIPTION)).get("productId"));
        return Long.parseLong(ok(api.createAuction(product, new BigDecimal("100000"), new BigDecimal("10000"), 5, "PUBLIC", "", anonymous)).get("auctionId"));
    }
    private static NetworkClient client(int port) throws Exception { var client = new NetworkClient(2_097_152, 3000, 6000); client.connect("127.0.0.1", port); return client; }
    private static ApiResponse ok(CompletableFuture<ApiResponse> task) throws Exception { var response = task.get(15, TimeUnit.SECONDS); TestSupport.check(response.isSuccess(), response.getErrorCode() + " " + response.getMessageText()); return response; }
    private static String labels(Scene scene) { return scene.getRoot().lookupAll(".label").stream().filter(node -> node instanceof Label).map(node -> ((Label) node).getText()).collect(java.util.stream.Collectors.joining(" ")); }
    private static void await(Callable<Boolean> condition, String label) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (System.nanoTime() < deadline) { if (fx(condition)) { return; } Thread.sleep(30); } throw new AssertionError("Timeout: " + label);
    }
    @SuppressWarnings("unchecked") private static <T> T field(Object object, String name, Class<T> type) throws Exception { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(object); }
    private static <T> T fx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>(); Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } }); return result.get(20, TimeUnit.SECONDS);
    }
    private static void snapshot(Scene scene, String name) throws Exception {
        scene.getRoot().applyCss(); scene.getRoot().layout(); var image = scene.snapshot(null);
        BufferedImage bitmap = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < bitmap.getHeight(); row++) { for (int column = 0; column < bitmap.getWidth(); column++) { bitmap.setRGB(column, row, image.getPixelReader().getArgb(column, row)); } }
        Path folder = Path.of("target", "social-room-preview"); Files.createDirectories(folder); ImageIO.write(bitmap, "png", folder.resolve(name + ".png").toFile());
    }
    private static final class TrackingTransport implements ClientTransport {
        private final ClientTransport delegate; private final AtomicInteger profileRequests = new AtomicInteger();
        TrackingTransport(ClientTransport delegate) { this.delegate = delegate; }
        @Override public CompletableFuture<WireMessage> sendRequest(MessageType type, Map<String, String> data) { if (type == MessageType.GET_ROOM_PROFILE) { profileRequests.incrementAndGet(); } return delegate.sendRequest(type, data); }
        @Override public CompletableFuture<Void> connect() { return delegate.connect(); }
        @Override public void disconnect() { delegate.disconnect(); }
        @Override public boolean isConnected() { return delegate.isConnected(); }
        @Override public ConnectionState getState() { return delegate.getState(); }
        @Override public String getEndpoint() { return delegate.getEndpoint(); }
        @Override public void setEndpoint(String endpoint) { delegate.setEndpoint(endpoint); }
        @Override public CompletableFuture<WireMessage> ping() { return delegate.ping(); }
        @Override public void addEventListener(Consumer<WireMessage> listener) { delegate.addEventListener(listener); }
        @Override public void addStateListener(ConnectionStateListener listener) { delegate.addStateListener(listener); }
        @Override public void close() { delegate.close(); }
    }
}
