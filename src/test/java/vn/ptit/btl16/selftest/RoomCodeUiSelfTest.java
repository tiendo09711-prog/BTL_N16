package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.ptit.btl16.client.fx.FxClientController;
import vn.ptit.btl16.client.fx.RoomCodeBadge;
import vn.ptit.btl16.client.fx.RoomCodeDialog;
import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.network.ConnectionState;
import vn.ptit.btl16.client.network.ConnectionStateListener;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.WireMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class RoomCodeUiSelfTest {
    private record View(Stage stage, FxClientController controller, ClientAppModel model, Network network) { }

    public static void main(String[] args) throws Exception {
        System.setProperty("btl16.client.manualConnect", "true");
        System.setProperty("btl16.client.sound.disabled", "true");
        System.setProperty("prism.order", "sw");
        AtomicReference<Throwable> uiFailure = new AtomicReference<>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> uiFailure.compareAndSet(null, failure));
        });
        View view = null;
        try {
            view = fx(() -> {
                var network = new Network();
                var model = new ClientAppModel();
                model.applyIdentity(new ApiResponse(response(MessageType.LOGIN_RESULT,
                        Map.of("success", "true", "userId", "2", "username", "buyer", "sessionToken", "room-code-session"))));
                var stage = new Stage();
                var controller = new FxClientController(stage, ClientConfig.loadDefault(), model, network,
                        new AccountApi(network), new AuctionApi(network));
                invoke(controller, "buildScenes");
                stage.setScene((Scene) field(controller, "mainScene"));
                stage.setWidth(1366); stage.setHeight(850); stage.show();
                return new View(stage, controller, model, network);
            });
            dialogInput(view);
            dialogCancellation(view);
            controllerCreate(view);
            createdBannerLifecycle(view);
            controllerJoin(view);
            fx(() -> { TestSupport.check(uiFailure.get() == null, "no asynchronous UI failures: " + uiFailure.get()); return null; });
            System.out.println("[PASS] RoomCodeUiSelfTest: six boxes, focus/backspace, paste, Enter, validation, retry, cancellation, direct room navigation, copy and created-banner lifecycle");
        } finally {
            View closing = view;
            fx(() -> {
                if (closing != null) {
                    for (Window window : java.util.List.copyOf(Window.getWindows())) { if (window != closing.stage()) { window.hide(); } }
                    closing.controller().close(); closing.stage().close();
                }
                return null;
            });
            Platform.exit();
        }
    }

    private static void dialogInput(View view) throws Exception {
        RoomCodeDialog dialog = fx(() -> {
            var result = new RoomCodeDialog(view.stage(), new AuctionApi(view.network()), () -> true);
            result.show(); return result;
        });
        fx(() -> {
            TestSupport.equals(6, dialog.getDialogPane().lookupAll(".room-code-digit").size(), "dialog contains exactly six boxes");
            TestSupport.check(((Label) dialog.getDialogPane().lookup(".detail-title")).getFont().getSize() <= 13, "room code prompt uses regular text size");
            confirm(dialog).fire();
            TestSupport.equals(0, view.network().lookups, "empty code never reaches server");
            TestSupport.check(error(dialog).isVisible(), "incomplete code shows validation");
            digit(dialog, 0).setText("a");
            TestSupport.equals("", digit(dialog, 0).getText(), "letters are rejected");
            digit(dialog, 0).setText("0");
            TestSupport.check(digit(dialog, 1).isFocused(), "typing moves focus to next box");
            digit(dialog, 1).fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.BACK_SPACE, false, false, false, false));
            TestSupport.equals("", digit(dialog, 0).getText(), "backspace in empty box clears previous digit");
            var clipboard = new ClipboardContent(); clipboard.putString("000007"); Clipboard.getSystemClipboard().setContent(clipboard);
            digit(dialog, 0).paste();
            for (int index = 0; index < 6; index++) { TestSupport.equals("000007".substring(index, index + 1), digit(dialog, index).getText(), "paste distributes digits"); }
            digit(dialog, 5).fireEvent(new ActionEvent());
            TestSupport.equals("000007", view.network().lastCode, "Enter submits exact code including leading zeroes");
            confirm(dialog).fire();
            TestSupport.equals(1, view.network().lookups, "pending lookup cannot submit twice");
            TestSupport.check(confirm(dialog).isDisabled(), "submit is disabled while lookup is pending");
            view.network().pending.complete(response(MessageType.ERROR,
                    Map.of("success", "false", "errorCode", "AUCTION_NOT_FOUND", "message", "ignored")));
            return null;
        });
        fx(() -> {
            TestSupport.check(dialog.isShowing(), "unknown code keeps popup open");
            TestSupport.equals("Ph\u00f2ng kh\u00f4ng t\u1ed3n t\u1ea1i", error(dialog).getText(), "unknown code displays exact requested message");
            screenshot(dialog.getDialogPane().getScene(), "dialog-not-found");
            TestSupport.check(error(dialog).localToScene(error(dialog).getBoundsInLocal()).getMaxY()
                    <= confirm(dialog).localToScene(confirm(dialog).getBoundsInLocal()).getMinY(), "error is readable above confirmation without scrolling");
            digit(dialog, 0).setText("000008");
            TestSupport.check(!error(dialog).isVisible(), "editing clears old feedback");
            confirm(dialog).fire();
            view.network().pending.completeExceptionally(new IllegalStateException("offline"));
            return null;
        });
        fx(() -> {
            TestSupport.check(error(dialog).isVisible() && !error(dialog).getText().equals("Ph\u00f2ng kh\u00f4ng t\u1ed3n t\u1ea1i"), "network outage is not reported as nonexistent room");
            confirm(dialog).fire();
            view.network().pending.complete(response(MessageType.AUCTION_CODE_RESULT, auction()));
            return null;
        });
        fx(() -> {
            TestSupport.check(!dialog.isShowing(), "successful code lookup closes popup");
            TestSupport.equals(42L, dialog.getResult(), "popup returns exact target room");
            return null;
        });
    }

    private static void dialogCancellation(View view) throws Exception {
        AtomicBoolean current = new AtomicBoolean(true);
        RoomCodeDialog canceled = fx(() -> {
            var dialog = new RoomCodeDialog(view.stage(), new AuctionApi(view.network()), current::get);
            dialog.show(); digit(dialog, 0).setText("123456"); confirm(dialog).fire();
            dialog.close();
            view.network().pending.complete(response(MessageType.AUCTION_CODE_RESULT, auction()));
            return dialog;
        });
        fx(() -> { TestSupport.equals(null, canceled.getResult(), "late response cannot navigate after cancel"); return null; });
        RoomCodeDialog expired = fx(() -> {
            var dialog = new RoomCodeDialog(view.stage(), new AuctionApi(view.network()), current::get);
            dialog.show(); digit(dialog, 0).setText("123456"); confirm(dialog).fire();
            current.set(false);
            view.network().pending.complete(response(MessageType.AUCTION_CODE_RESULT, auction()));
            return dialog;
        });
        fx(() -> {
            TestSupport.check(!expired.isShowing() && expired.getResult() == null, "stale session cannot return a room");
            return null;
        });
    }

    private static void controllerJoin(View view) throws Exception {
        fx(() -> {
            Scene scene = view.stage().getScene(); scene.getRoot().applyCss(); scene.getRoot().layout();
            var search = scene.lookup("#marketplace-search-input");
            var button = (Button) scene.lookup("#marketplace-enter-room-code");
            TestSupport.check(button != null && button.isVisible(), "marketplace exposes enter-code button");
            TestSupport.check(button.localToScene(button.getBoundsInLocal()).getMinX() > search.localToScene(search.getBoundsInLocal()).getMaxX(), "button sits next to search");
            ((TextField) search).setText("a search that does not match any room");
            screenshot(scene, "lobby");
            Platform.runLater(button::fire);
            return null;
        });
        fx(() -> {
            var dialog = Window.getWindows().stream().filter(window -> window != view.stage() && window.isShowing()
                    && window.getScene().lookup("#room-code-dialog") != null).findFirst().orElseThrow().getScene();
            ((TextField) dialog.lookup("#room-code-digit-0")).setText("000007");
            ((Button) dialog.lookup("#room-code-confirm")).fire();
            view.network().pending.complete(response(MessageType.AUCTION_CODE_RESULT, auction()));
            return null;
        });
        drain(); drain();
        fx(() -> {
            TestSupport.equals(1, view.network().joins, "confirmed code invokes real join exactly once");
            TestSupport.equals(42L, view.model().getJoinedAuctionId(), "code navigates directly even when room is absent from filtered list");
            var badge = (RoomCodeBadge) view.stage().getScene().lookup("#room-code-badge");
            TestSupport.check(badge != null && badge.isVisible(), "joined room visibly displays room code");
            TestSupport.check(badge.lookup("#room-code-badge-close") == null, "room details code is not dismissible");
            TestSupport.equals("000007", ((Label) badge.lookup(".room-code-value")).getText(), "room badge displays all six digits");
            ((Button) badge.lookup("#room-code-badge-copy")).fire();
            TestSupport.equals("000007", Clipboard.getSystemClipboard().getString(), "copy action copies exact six-digit code");
            screenshot(view.stage().getScene(), "joined-room");
            return null;
        });
    }

    private static void controllerCreate(View view) throws Exception {
        int expectedCreations = view.network().creations + 1;
        String expectedCode = view.network().createdAuction.get("roomCode");
        fx(() -> {
            Platform.runLater(() -> {
                try { invoke(view.controller(), "showCreateAuction"); }
                catch (Exception failure) { throw new RuntimeException(failure); }
            });
            return null;
        });
        drain();
        fx(() -> {
            var dialog = Window.getWindows().stream().filter(window -> window != view.stage() && window.isShowing()
                    && window.getScene().lookup("#auction-creation-dialog") != null).findFirst().orElseThrow().getScene();
            ((TextField) dialog.lookup("#auction-create-max-participants")).setText("20");
            ((Button) dialog.lookup("#auction-create-next")).fire();
            ((Button) dialog.lookup("#auction-create-confirm")).fire();
            return null;
        });
        drain(); drain();
        fx(() -> {
            TestSupport.equals(expectedCreations, view.network().creations, "real creation callback completes");
            TestSupport.equals(0, view.network().joins, "creating a room does not silently enter it");
            var badge = (RoomCodeBadge) view.stage().getScene().lookup("#created-room-code");
            TestSupport.check(badge != null && badge.isVisible() && badge.isManaged(), "room code appears immediately after creation without selecting a room");
            TestSupport.equals(expectedCode, ((Label) badge.lookup(".room-code-value")).getText(), "creation banner shows server-generated code");
            ((Button) badge.lookup("#created-room-code-copy")).fire();
            TestSupport.equals(expectedCode, Clipboard.getSystemClipboard().getString(), "seller can copy newly created code");
            screenshot(view.stage().getScene(), "created-room");
            return null;
        });
    }

    private static void createdBannerLifecycle(View view) throws Exception {
        fx(() -> {
            var badge = (RoomCodeBadge) field(view.controller(), "createdRoomCode");
            var close = (Button) badge.lookup("#created-room-code-close");
            TestSupport.check(close != null && close.isVisible(), "created banner has a close button");
            TestSupport.check(close.localToScene(close.getBoundsInLocal()).getMinX()
                    > badge.lookup("#created-room-code-copy").localToScene(badge.lookup("#created-room-code-copy").getBoundsInLocal()).getMaxX(),
                    "close button sits to the right of copy");
            close.fire();
            invoke(view.controller(), "renderAuctions");
            checkCreatedBannerHidden(view, "dismissed banner stays hidden after refresh");
            return null;
        });
        for (int auctionId = 43; auctionId <= 44; auctionId++) {
            view.network().createdAuction = auction(auctionId, "0000" + auctionId, "OPEN");
            controllerCreate(view);
        }
        fx(() -> {
            TestSupport.equals(1, view.stage().getScene().getRoot().lookupAll("#created-room-code").size(), "new room replaces the previous banner");
            serverEvent(view, MessageType.AUCTION_ENDED, auction(43, "000043", "ENDED"));
            var badge = (RoomCodeBadge) field(view.controller(), "createdRoomCode");
            TestSupport.check(badge.isVisible(), "ending the previous room does not hide the newest code");
            TestSupport.equals("000044", ((Label) badge.lookup(".room-code-value")).getText(), "only newest room code remains");
            serverEvent(view, MessageType.AUCTION_ENDED, auction(44, "000044", "ENDED"));
            checkCreatedBannerHidden(view, "ended room automatically hides banner");
            return null;
        });
        int auctionId = 45;
        for (MessageType event : java.util.List.of(MessageType.AUCTION_CANCELLED, MessageType.AUCTION_ARCHIVED)) {
            var created = auction(auctionId++, "000045", "OPEN");
            view.network().createdAuction = created;
            controllerCreate(view);
            fx(() -> {
                var closed = new LinkedHashMap<>(created);
                closed.put("status", "CANCELLED"); closed.put("version", "2");
                serverEvent(view, event, closed);
                checkCreatedBannerHidden(view, event + " automatically hides banner");
                return null;
            });
        }
        view.network().createdAuction = auction(48, "000048", "OPEN");
        controllerCreate(view);
        fx(() -> {
            var ended = auction(48, "000048", "ENDED");
            var apply = FxClientController.class.getDeclaredMethod("applySnapshot", ApiResponse.class, boolean.class);
            apply.setAccessible(true);
            apply.invoke(view.controller(), new ApiResponse(response(MessageType.AUCTION_SNAPSHOT, ended)), false);
            invoke(view.controller(), "renderAuctions");
            checkCreatedBannerHidden(view, "closed snapshot hides banner even without an end event");
            return null;
        });
    }

    private static void checkCreatedBannerHidden(View view, String message) throws Exception {
        var badge = (RoomCodeBadge) field(view.controller(), "createdRoomCode");
        TestSupport.check(!badge.isVisible() && !badge.isManaged(), message + " without leaving empty space");
    }

    private static void serverEvent(View view, MessageType type, Map<String, String> data) throws Exception {
        var handler = FxClientController.class.getDeclaredMethod("handleServerEvent", WireMessage.class);
        handler.setAccessible(true); handler.invoke(view.controller(), response(type, data));
    }

    private static Map<String, String> auction(int auctionId, String code, String status) {
        var data = auction();
        data.put("auctionId", Integer.toString(auctionId)); data.put("roomCode", code); data.put("status", status);
        if (!"OPEN".equals(status)) { data.put("version", "2"); }
        return data;
    }

    private static Map<String, String> auction() {
        var data = new LinkedHashMap<String, String>();
        data.put("success", "true"); data.put("auctionId", "42"); data.put("roomCode", "000007");
        data.put("productId", "1"); data.put("productCode", "MH1"); data.put("productName", "Room code test product");
        data.put("description", "A product description for the room code navigation test.");
        data.put("hostUserId", "1"); data.put("hostUsername", "seller");
        data.put("startPrice", "100"); data.put("currentPrice", "100"); data.put("minBidIncrement", "10");
        data.put("startTime", Instant.now().minusSeconds(60).toString()); data.put("endTime", Instant.now().plusSeconds(3600).toString());
        data.put("status", "OPEN"); data.put("visibility", "PUBLIC"); data.put("watcherCount", "1");
        data.put("maxParticipants", "20"); data.put("version", "1");
        return data;
    }

    private static TextField digit(RoomCodeDialog dialog, int index) { return (TextField) dialog.getDialogPane().lookup("#room-code-digit-" + index); }
    private static Button confirm(RoomCodeDialog dialog) { return (Button) dialog.getDialogPane().lookup("#room-code-confirm"); }
    private static Label error(RoomCodeDialog dialog) { return (Label) dialog.getDialogPane().lookup("#room-code-error"); }
    private static Object field(Object target, String name) throws Exception { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static void invoke(Object target, String name) throws Exception { var method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(target); }
    private static void drain() throws Exception { fx(() -> null); fx(() -> null); }
    private static <Result> Result fx(Callable<Result> action) throws Exception {
        var result = new CompletableFuture<Result>();
        Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable failure) { result.completeExceptionally(failure); } });
        return result.get(30, TimeUnit.SECONDS);
    }

    private static void screenshot(Scene scene, String name) throws Exception {
        scene.getRoot().applyCss(); scene.getRoot().layout();
        var image = scene.snapshot(null);
        var bitmap = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < bitmap.getHeight(); row++) {
            for (int column = 0; column < bitmap.getWidth(); column++) { bitmap.setRGB(column, row, image.getPixelReader().getArgb(column, row)); }
        }
        Path directory = Path.of("target", "room-code-preview"); Files.createDirectories(directory);
        javax.imageio.ImageIO.write(bitmap, "png", directory.resolve(name + ".png").toFile());
    }

    private static WireMessage response(MessageType type, Map<String, String> data) { return WireMessage.response(type, null, 1, data); }

    private static final class Network implements ClientTransport {
        private CompletableFuture<WireMessage> pending;
        private int lookups;
        private int joins;
        private int creations;
        private String lastCode;
        private Map<String, String> createdAuction = auction();

        @Override public CompletableFuture<WireMessage> sendRequest(MessageType type, Map<String, String> data) {
            if (type == MessageType.MY_PRODUCTS) {
                var products = new LinkedHashMap<String, String>();
                products.put("success", "true"); products.put("productCount", "1"); products.put("product.0.productId", "1");
                products.put("product.0.ownerId", "2"); products.put("product.0.code", "MH1"); products.put("product.0.name", "Room code test product");
                products.put("product.0.active", "true"); products.put("product.0.transactionStatus", "UNTRADED");
                return CompletableFuture.completedFuture(response(MessageType.MY_PRODUCTS_RESULT, products));
            }
            if (type == MessageType.PING) {
                return CompletableFuture.completedFuture(response(MessageType.PONG,
                        Map.of("success", "true", "roomAdmission", "true", "auctionScheduling", "true")));
            }
            if (type == MessageType.CREATE_AUCTION) {
                creations++; return CompletableFuture.completedFuture(response(MessageType.CREATE_AUCTION_RESULT, createdAuction));
            }
            if (type == MessageType.FIND_AUCTION_BY_CODE) {
                lookups++; lastCode = data.get("roomCode"); pending = new CompletableFuture<>(); return pending;
            }
            if (type == MessageType.JOIN_AUCTION) {
                joins++; TestSupport.equals("42", data.get("auctionId"), "join uses resolved auction ID");
                return CompletableFuture.completedFuture(response(MessageType.AUCTION_SNAPSHOT, auction()));
            }
            return CompletableFuture.completedFuture(response(type,
                    Map.of("success", "true", "count", "0", "balance", "1000000", "available", "1000000", "reserved", "0")));
        }
        @Override public CompletableFuture<Void> connect() { return CompletableFuture.completedFuture(null); }
        @Override public void disconnect() { }
        @Override public boolean isConnected() { return true; }
        @Override public ConnectionState getState() { return ConnectionState.CONNECTED; }
        @Override public String getEndpoint() { return "ws://127.0.0.1:8890/ws"; }
        @Override public void setEndpoint(String endpoint) { }
        @Override public CompletableFuture<WireMessage> ping() { return new CompletableFuture<>(); }
        @Override public void addEventListener(Consumer<WireMessage> listener) { }
        @Override public void addStateListener(ConnectionStateListener listener) { }
        @Override public void close() { }
    }
}
