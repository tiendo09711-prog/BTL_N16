package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.image.ImageView;
import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import vn.ptit.btl16.client.fx.FxClientController;
import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.model.ClientAuction;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.fx.ServerFxApp;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class DesktopUiSelfTest {
    private DesktopUiSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of("target", "desktop-preview").toAbsolutePath();
        Files.createDirectories(output);
        Path data = Files.createTempDirectory("btl16-ui-test-");
        Path config = data.resolve("server.properties");
        Files.writeString(config, "server.tcp.enabled=false\nserver.websocket.port=0\nmedia.port=0\ndb.driver=org.h2.Driver\n"
                + "db.user=sa\ndb.name=auction\nsecurity.passwordIterations=10000\n");
        System.setProperty("btl16.server.config", config.toString());
        System.setProperty("btl16.dataDir", data.toString());
        System.setProperty("btl16.client.manualConnect", "true");
        Platform.startup(() -> Platform.setImplicitExit(false));
        ServerFxApp serverUi = new ServerFxApp();
        Stage serverStage = fx(() -> {
            Stage stage = new Stage();
            serverUi.start(stage);
            return stage;
        });
        FxClientController controller = null;
        Stage clientStage = null;
        try {
            TestSupport.waitUntil(() -> read(serverUi, "application") != null, 15000, "JavaFX server startup");
            ServerApplication server = read(serverUi, "application");
            String endpoint = "ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws";
            try (WebSocketClientTransport host = new WebSocketClientTransport(endpoint, 2097152, 5000, 10000);
                 WebSocketClientTransport buyer = new WebSocketClientTransport(endpoint, 2097152, 5000, 10000)) {
                host.connect().get(10, TimeUnit.SECONDS);
                buyer.connect().get(10, TimeUnit.SECONDS);
                AccountApi hostAccount = new AccountApi(host);
                AccountApi buyerAccount = new AccountApi(buyer);
                ok(hostAccount.register("studio", "Password123!", "Studio", "", ""));
                ok(hostAccount.login("studio", "Password123!"));
                ok(buyerAccount.register("linh", "Password123!", "Linh", "", ""));
                ApiResponse identity = ok(buyerAccount.login("linh", "Password123!"));
                TestMedia.fundWallets(server.wallets());
                AuctionApi hostApi = new AuctionApi(host);
                long firstRoom = 0;
                String[] names = {"Tai nghe Studio", "Máy ảnh Compact", "Bàn phím cơ"};
                for (int index = 0; index < names.length; index++) {
                    ApiResponse product = ok(TestMedia.create(hostApi, "VISUAL_" + index, names[index],
                            "Phiên đấu giá trực tiếp. Sản phẩm còn nguyên hộp, đầy đủ phụ kiện."));
                    ApiResponse room = ok(hostApi.createAuction(Long.parseLong(product.get("productId")),
                            new BigDecimal("100000"), new BigDecimal("10000"), 5 + index));
                    if (index == 0) firstRoom = Long.parseLong(room.get("auctionId"));
                }
                ClientAppModel model = new ClientAppModel();
                clientStage = fx(Stage::new);
                Stage currentStage = clientStage;
                controller = fx(() -> {
                    FxClientController current = new FxClientController(currentStage, ClientConfig.loadDefault(),
                            model, buyer, buyerAccount, new AuctionApi(buyer));
                    current.start();
                    return current;
                });
                Thread.sleep(800);
                snapshot(clientStage, output.resolve("client-login.png"));
                FxClientController current = controller;
                fx(() -> {
                    model.applyIdentity(identity);
                    invoke(current, "showMain", new Class<?>[0]);
                    invoke(current, "loadAuctionList", new Class<?>[0]);
                    return null;
                });
                TestSupport.waitUntil(() -> model.auctionSnapshot().size() == 3, 10000, "client room list");
                snapshot(clientStage, output.resolve("client-marketplace.png"));
                ApiResponse joined = ok(new AuctionApi(buyer).join(firstRoom));
                fx(() -> {
                    invoke(current, "applySnapshot", new Class<?>[]{ApiResponse.class, boolean.class}, joined, true);
                    invoke(current, "renderAuctions", new Class<?>[0]);
                    TableView<?> table = read(current, "auctionTable");
                    table.getSelectionModel().selectFirst();
                    return null;
                });
                ok(new AuctionApi(buyer).bid(firstRoom, new BigDecimal("110000")));
                TestSupport.waitUntil(() -> {
                    ImageView view = read(current, "productImage");
                    if (view.getImage() != null && view.getImage().isError()) {
                        throw new AssertionError("Room cover cannot load", view.getImage().getException());
                    }
                    return view.getImage() != null && view.getImage().getProgress() == 1;
                }, 10000, "room loads full-size cover image");
                Thread.sleep(1500);
                fx(() -> {
                    TestSupport.check((boolean) read(current, "roomMode"), "joining switches to dedicated room layout");
                    javafx.scene.control.SplitPane split = read(current, "listingSplit");
                    TestSupport.equals(1, split.getItems().size(), "room hides marketplace table");
                    javafx.scene.layout.VBox history = read(current, "roomHistory");
                    TestSupport.check(history.getChildren().size() > 1, "room displays live bid history");
                    javafx.scene.layout.FlowPane suggestions = read(current, "bidSuggestions");
                    TestSupport.equals(3, suggestions.getChildren().size(), "room offers three bid increment shortcuts");
                    ImageView image = read(current, "productImage");
                    TestSupport.check(image.getBoundsInParent().getWidth() > 10 && image.getBoundsInParent().getHeight() > 10,
                            "loaded room cover has visible layout bounds");
                    return null;
                });
                snapshot(clientStage, output.resolve("client-auctions.png"));
                snapshot(serverStage, output.resolve("server-dashboard.png"));
                fx(() -> {
                    currentStage.setWidth(1100);
                    currentStage.setHeight(740);
                    return null;
                });
                Thread.sleep(500);
                snapshot(clientStage, output.resolve("client-compact.png"));
                fx(() -> { invoke(current, "leaveJoined", new Class<?>[0]); return null; });
                TestSupport.waitUntil(() -> model.getJoinedAuctionId() == null, 10000, "leave room response");
                fx(() -> {
                    TestSupport.check(!(boolean) read(current, "roomMode"), "leaving restores marketplace layout");
                    javafx.scene.control.SplitPane split = read(current, "listingSplit");
                    TestSupport.equals(2, split.getItems().size(), "marketplace table returns after leaving");
                    return null;
                });
                long closingRoom = model.auctionSnapshot().stream().filter(value -> value.getCurrentWinnerId() == null)
                        .findFirst().orElseThrow().getAuctionId();
                ApiResponse closingJoin = ok(new AuctionApi(buyer).join(closingRoom));
                fx(() -> {
                    invoke(current, "applySnapshot", new Class<?>[]{ApiResponse.class, boolean.class}, closingJoin, true);
                    invoke(current, "renderAuctions", new Class<?>[0]);
                    TableView<ClientAuction> table = read(current, "auctionTable");
                    table.getSelectionModel().select(model.joinedAuction());
                    return null;
                });
                ok(hostApi.endAuction(closingRoom));
                TestSupport.waitUntil(() -> model.joinedAuction() != null && !model.joinedAuction().isOpen(),
                        10000, "room receives the ended event");
                ApiResponse openRooms = ok(new AuctionApi(buyer).listAuctions());
                fx(() -> {
                    invoke(current, "applyAuctionList", new Class<?>[]{ApiResponse.class}, openRooms);
                    invoke(current, "renderAuctions", new Class<?>[0]);
                    TestSupport.check(model.joinedAuction() != null, "marketplace refresh keeps joined closed-room result");
                    var endedAt = model.joinedAuction().getEndedAt();
                    model.updateServerClock(endedAt.plusSeconds(30));
                    invoke(current, "refreshClock", new Class<?>[0]);
                    javafx.scene.control.Label countdown = read(current, "detailTime");
                    TestSupport.check(countdown.getText().matches(".*00:[23][0-9]"), "closed room displays the remaining one-minute countdown");
                    javafx.scene.control.Button bid = read(current, "bidButton");
                    TestSupport.check(bid.isDisabled(), "closed result never accepts more bids");
                    model.updateServerClock(endedAt.plusSeconds(61));
                    invoke(current, "refreshClock", new Class<?>[0]);
                    TestSupport.check(model.getJoinedAuctionId() == null && !(boolean) read(current, "roomMode"),
                            "countdown returns to marketplace without waiting a real minute in this UI test");
                    TestSupport.check(model.auctionSnapshot().stream().noneMatch(value -> value.getAuctionId() == closingRoom),
                            "closed room disappears after the countdown");
                    return null;
                });
                ok(new AuctionApi(buyer).leave(closingRoom));
                System.out.println("[PASS] JavaFX login, populated auction view, compact window, server dashboard and 60-second result countdown: " + output);
            }
        } finally {
            FxClientController current = controller;
            Stage currentStage = clientStage;
            fx(() -> {
                if (current != null) current.close();
                if (currentStage != null) currentStage.hide();
                serverUi.stop();
                serverStage.hide();
                return null;
            });
            Platform.exit();
            try (var files = Files.walk(data)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }

    private static void snapshot(Stage stage, Path file) throws Exception {
        fx(() -> {
            Scene scene = stage.getScene();
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            var image = scene.snapshot(null);
            int width = (int) image.getWidth();
            int height = (int) image.getHeight();
            BufferedImage bitmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    bitmap.setRGB(column, row, image.getPixelReader().getArgb(column, row));
                }
            }
            ImageIO.write(bitmap, "png", file.toFile());
            return null;
        });
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result.get(20, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private static <T> T read(Object object, String name) {
        try {
            var field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(object);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void invoke(Object object, String name, Class<?>[] types, Object... values) throws Exception {
        var method = object.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(object, values);
    }

    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception {
        ApiResponse response = future.get(15, TimeUnit.SECONDS);
        TestSupport.check(response.isSuccess(), response.getMessageText());
        return response;
    }
}
