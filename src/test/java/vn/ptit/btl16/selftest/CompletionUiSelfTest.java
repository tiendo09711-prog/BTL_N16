package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.*;
import vn.ptit.btl16.client.fx.*;
import vn.ptit.btl16.client.model.*;
import vn.ptit.btl16.client.network.*;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.protocol.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class CompletionUiSelfTest {
    private record Pending(MessageType type, Map<String,String> data, CompletableFuture<WireMessage> future) {
        void reply(Map<String,String> values) { Map<String,String> result = new LinkedHashMap<>(values); result.put("success", "true"); future.complete(WireMessage.response(type, null, 1, result)); }
    }
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false)); System.setProperty("btl16.client.manualConnect", "true");
        try {
            var legacy = new Network(); legacy.supportsScheduling = false;
            var rejected = new AuctionApi(legacy).createAuction(1, java.math.BigDecimal.TEN, java.math.BigDecimal.ONE, 5,
                    "PUBLIC", "", false, java.time.Instant.now().plusSeconds(600)).get(5, TimeUnit.SECONDS);
            TestSupport.check(!rejected.isSuccess() && rejected.getMessageText().contains("Server"), "old server receives actionable compatibility error");
            TestSupport.equals(0, legacy.count(MessageType.CREATE_AUCTION), "old server is blocked before it can create an immediate room");
            var network = new Network(); var active = new AtomicBoolean(true);
            var inbox = fx(() -> { var pane = new PersonalNotificationsPane(null, network, active::get, (kind, room) -> { }); pane.show(); return pane; });
            network.take(MessageType.GET_MY_NOTIFICATIONS).reply(notifications("OUTBID")); drain();
            TestSupport.equals(1, fx(() -> table(inbox, "table").getItems().size()), "inbox renders stored notification once");
            TestSupport.equals("Thông báo cá nhân", fx(() -> ((Stage) field(inbox, "stage")).getTitle()), "Vietnamese inbox title");
            TestSupport.equals("NỘI DUNG", fx(() -> table(inbox, "table").getColumns().get(2).getText()), "Vietnamese column heading");
            TestSupport.equals("Bạn đã bị vượt giá", fx(() -> table(inbox, "table").getColumns().get(2).getCellData(0)), "Vietnamese notification content");
            screenshot(inbox, "target/completion-inbox-vietnamese.png");
            fx(() -> { invoke(inbox, "load", new Class<?>[]{long.class}, Long.MAX_VALUE); return null; });
            network.take(MessageType.GET_MY_NOTIFICATIONS).reply(notifications("OUTBID")); drain();
            TestSupport.equals(1, fx(() -> table(inbox, "table").getItems().size()), "refresh/reconnect does not duplicate rows");
            fx(() -> { invoke(inbox, "load", new Class<?>[]{long.class}, Long.MAX_VALUE); return null; });
            active.set(false); network.take(MessageType.GET_MY_NOTIFICATIONS).reply(notifications("AUCTION_RESULT")); drain();
            TestSupport.check(!fx(() -> ((Stage) field(inbox, "stage")).isShowing()), "old session inbox closes before rendering");
            TestSupport.equals(0, fx(() -> table(inbox, "table").getItems().size()), "closed inbox clears private rows");
            active.set(true);
            var pagedInbox = fx(() -> { var pane = new PersonalNotificationsPane(null, network, active::get, (kind, room) -> { }); pane.show(); return pane; });
            network.take(MessageType.GET_MY_NOTIFICATIONS).reply(notifications("OUTBID")); drain();
            fx(() -> { invoke(pagedInbox, "load", new Class<?>[]{long.class}, 10L); return null; });
            network.take(MessageType.GET_MY_NOTIFICATIONS).reply(notifications("OUTBID")); drain(); active.set(false);
            fx(() -> { var poll = (javafx.animation.Timeline) field(pagedInbox, "poll"); poll.getKeyFrames().get(0).getOnFinished().handle(new javafx.event.ActionEvent()); return null; });
            TestSupport.check(!fx(() -> ((Stage) field(pagedInbox, "stage")).isShowing()), "idle second-page inbox closes on session expiry without fetching");
            TestSupport.equals(0, fx(() -> table(pagedInbox, "table").getItems().size()), "idle session invalidation clears inbox cache");
            var wallet = fx(() -> { var pane = new TransactionsPane(new WalletApi(network), 2, "test", () -> true); var window = new Stage(); var scene = new Scene(pane, 1100, 740); vn.ptit.btl16.common.fx.DesktopTheme.apply(scene); window.setScene(scene); window.show(); pane.start(); return pane; });
            network.take(MessageType.GET_WALLET_ENTRIES).reply(ledger("INITIAL")); drain();
            fx(() -> { ((TextField) field(wallet, "room")).setText("1"); invoke(wallet, "applyFilters", new Class<?>[0]); ((TextField) field(wallet, "room")).setText("2"); invoke(wallet, "applyFilters", new Class<?>[0]); return null; });
            var old = network.take(MessageType.GET_WALLET_ENTRIES); var latest = network.take(MessageType.GET_WALLET_ENTRIES);
            TestSupport.equals("2", latest.data().get("auctionId"), "wallet sends selected session filter"); old.reply(ledger("OLD")); drain();
            TestSupport.equals(0, fx(() -> table(wallet, "entries").getItems().size()), "stale ledger response ignored"); latest.reply(ledger("BUY:2")); drain();
            TestSupport.equals(1, fx(() -> table(wallet, "entries").getItems().size()), "current filtered ledger rendered");
            screenshot(wallet, "target/completion-transactions.png"); fx(() -> { wallet.close(); ((Stage) wallet.getScene().getWindow()).close(); return null; });
            var model = new ClientAppModel(); model.applyIdentity(new ApiResponse(WireMessage.response(MessageType.LOGIN_RESULT, null, 1, Map.of("success", "true", "userId", "2", "username", "test", "sessionToken", "same-session"))));
            var controller = fx(() -> { var stage = new Stage(); var view = new FxClientController(stage, ClientConfig.loadDefault(), model, network, new AccountApi(network), new AuctionApi(network)); invoke(view, "buildScenes", new Class<?>[0]); stage.setScene((Scene) field(view, "mainScene")); stage.setWidth(1100); stage.setHeight(740); stage.show(); return view; });
            fx(() -> {
                var now = java.time.Instant.now(); List<ClientAuction> rooms = new ArrayList<>();
                for (int index = 1; index <= 75; index++) { rooms.add(new ClientAuction(index, index, "CODE", "Product " + index, "", 1, "host", java.math.BigDecimal.ONE, java.math.BigDecimal.ONE, java.math.BigDecimal.valueOf(index), null, "", now, now.plusSeconds(index + 600), "OPEN", null, 1, 0)); }
                model.replaceAuctions(rooms, now); model.applySnapshot(rooms.get(74), List.of(), now, true);
                invoke(controller, "renderAuctions", new Class<?>[0]);
                @SuppressWarnings("unchecked") var visibility = (ComboBox<String>) field(controller, "lobbyVisibility"); visibility.setValue("PRIVATE");
                var selected = (ClientAuction) ((AuctionMarketplacePane) field(controller, "auctionCards")).getSelectionModel().getSelectedItem();
                TestSupport.equals(75L, selected.getAuctionId(), "active live room survives filter excluding it and being beyond first page");
                TestSupport.check(((Label) field(controller, "detailName")).getText().contains("#75"), "active product/result details are not cleared by lobby paging");
                model.leaveJoinedAuction(); visibility.setValue("ALL"); invoke(controller, "renderAuctions", new Class<?>[0]);
                TestSupport.equals(30, ((AuctionMarketplacePane) field(controller, "auctionCards")).getItems().size(), "leaving restores bounded lobby page"); return null;
            });
            fx(() -> {
                var now = java.time.Instant.now(); var start = now.plusSeconds(60);
                var scheduled = new ClientAuction(99, 99, "SCHEDULE", "Scheduled product", "", 1, "host", java.math.BigDecimal.ONE,
                        java.math.BigDecimal.ONE, java.math.BigDecimal.ONE, null, "", start, start.plusSeconds(300), "OPEN", null, 1, 0);
                var later = new ClientAuction(100, 100, "LATER", "Later product", "", 1, "host", java.math.BigDecimal.TEN,
                        java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, null, "", start.plusSeconds(120), start.plusSeconds(420), "OPEN", null, 1, 0);
                model.replaceAuctions(List.of(later, scheduled), now); invoke(controller, "renderAuctions", new Class<?>[0]);
                var scene = ((Stage) field(controller, "stage")).getScene();
                @SuppressWarnings("unchecked") var order = (ComboBox<String>) field(controller, "lobbyOrder"); order.setValue("PRICE_DESC");
                ((ToggleButton) scene.lookup("#marketplace-phase-upcoming")).fire();
                var gallery = (AuctionMarketplacePane) field(controller, "auctionCards");
                TestSupport.equals(2, gallery.getItems().size(), "scheduled rooms appear in upcoming tab");
                TestSupport.equals(99L, gallery.getItems().get(0).getAuctionId(), "earlier start comes first despite later room having newer id and higher price");
                TestSupport.check(order.isDisabled() && order.getValue().equals("STARTING"), "upcoming sort consistently shows nearest start");
                TestSupport.check(((Button) field(controller, "joinButton")).isDisabled(), "scheduled preview cannot join early");
                model.replaceAuctions(List.of(later, scheduled), start.plusSeconds(1)); invoke(controller, "refreshClock", new Class<?>[0]);
                TestSupport.equals(1, gallery.getItems().size(), "clock automatically removes only the started room from upcoming");
                TestSupport.equals(100L, gallery.getItems().get(0).getAuctionId(), "later room remains upcoming until its own time");
                ((ToggleButton) scene.lookup("#marketplace-phase-live")).fire();
                TestSupport.check(!order.isDisabled() && order.getValue().equals("PRICE_DESC"), "leaving upcoming restores previous sort choice");
                TestSupport.equals(1, ((AuctionMarketplacePane) field(controller, "auctionCards")).getItems().size(), "started room appears in live tab");
                TestSupport.check(!((Button) field(controller, "joinButton")).isDisabled(), "join enabled at scheduled start");
                model.replaceAuctions(List.of(), now); invoke(controller, "renderAuctions", new Class<?>[0]); return null;
            });
            eligibilityNotice(controller, network);
            fx(() -> { invoke(controller, "showCreateAuction", new Class<?>[0]); invoke(controller, "showCreateAuction", new Class<?>[0]); return null; });
            TestSupport.equals(1, network.count(MessageType.MY_PRODUCTS), "create guard prevents duplicate product form load");
            network.take(MessageType.MY_PRODUCTS).reply(product()); drain();
            fx(() -> {
                var scene = dialogButton(ButtonBar.ButtonData.OK_DONE).getScene();
                var mode = (ComboBox<?>) scene.lookup("#auction-start-mode");
                TestSupport.check(!scene.lookup("#auction-create-deposit").isVisible() || !scene.lookup("#auction-create-deposit").getParent().getParent().isVisible(), "immediate rooms do not show deposit");
                dialogButton(ButtonBar.ButtonData.OK_DONE).fire();
                TestSupport.check(scene.lookup("#auction-create-error").isVisible(), "capacity is required before review");
                ((TextField) scene.lookup("#auction-create-max-participants")).setText("12");
                TestSupport.equals(0, mode.getSelectionModel().getSelectedIndex(), "immediate start remains default");
                TestSupport.check(scene.lookup("#auction-start-date").isDisabled(), "date disabled for immediate start");
                TestSupport.equals(4, scene.getRoot().lookupAll(".auction-create-section").size(), "creation fields are grouped into four sections");
                var owner = ((Stage) field(controller, "stage")).getScene();
                TestSupport.check(owner.lookup("#auction-create-backdrop") != null, "creation dialog dims the main interface");
                TestSupport.check(((javafx.scene.layout.StackPane) owner.getRoot()).getChildren().getFirst().getEffect() instanceof javafx.scene.effect.GaussianBlur, "main interface is blurred behind the modal");
                ((TextField) scene.lookup("#auction-create-price")).setText("150000");
                screenshot(scene.getRoot(), "target/auction-create-form.png");
                dialogButton(ButtonBar.ButtonData.OK_DONE).fire(); return null;
            }); drain();
            fx(() -> {
                var scene = dialogButton(ButtonBar.ButtonData.OK_DONE).getScene();
                TestSupport.check(scene.lookup("#auction-create-review") != null, "creation opens a styled summary before submission");
                screenshot(scene.getRoot(), "target/auction-create-review.png");
                dialogButton(ButtonBar.ButtonData.CANCEL_CLOSE).fire();
                TestSupport.equals("150000", ((TextField) scene.lookup("#auction-create-price")).getText(), "back keeps the edited price");
                ((RadioButton) scene.lookup("#auction-create-private")).fire();
                var password = (PasswordField) scene.lookup("#auction-create-password");
                var repeat = (PasswordField) scene.lookup("#auction-create-password-confirm");
                TestSupport.check(!password.isDisabled(), "private mode enables password fields");
                password.setText("private-secret"); repeat.setText("wrong-secret");
                dialogButton(ButtonBar.ButtonData.OK_DONE).fire();
                TestSupport.check(scene.lookup("#auction-create-error").isVisible(), "mismatched passwords stay in the editor");
                repeat.setText("private-secret"); dialogButton(ButtonBar.ButtonData.OK_DONE).fire();
                String summary = scene.lookup("#auction-create-review").lookupAll(".label").stream().map(node -> ((Label) node).getText()).collect(java.util.stream.Collectors.joining(" "));
                TestSupport.check(summary.contains("Riêng tư") && !summary.contains("private-secret"), "review describes private mode without exposing its password");
                dialogButton(ButtonBar.ButtonData.CANCEL_CLOSE).fire();
                TestSupport.equals("private-secret", ((PasswordField) scene.lookup("#auction-create-password")).getText(), "back keeps private room settings");
                ((Stage) scene.getWindow()).setHeight(650); scene.getRoot().applyCss(); scene.getRoot().layout();
                var nextBounds = dialogButton(ButtonBar.ButtonData.OK_DONE).localToScene(dialogButton(ButtonBar.ButtonData.OK_DONE).getLayoutBounds());
                TestSupport.check(nextBounds.getMaxY() <= scene.getHeight(), "creation action remains accessible in a shorter window");
                screenshot(scene.getRoot(), "target/auction-create-small.png");
                ((Button) scene.lookup("#auction-create-close")).fire(); return null;
            }); drain();
            TestSupport.equals(0, network.count(MessageType.CREATE_AUCTION), "cancelling immediate review does not create room");
            fx(() -> {
                var owner = ((Stage) field(controller, "stage")).getScene();
                TestSupport.check(owner.lookup("#auction-create-backdrop") == null
                        && ((javafx.scene.layout.StackPane) owner.getRoot()).getChildren().getFirst().getEffect() == null, "closing restores the original main interface");
                return null;
            });
            fx(() -> { invoke(controller, "showCreateAuction", new Class<?>[0]); return null; });
            network.take(MessageType.MY_PRODUCTS).reply(product()); drain();
            fx(() -> {
                var scene = dialogButton(ButtonBar.ButtonData.OK_DONE).getScene();
                ((ComboBox<?>) scene.lookup("#auction-start-mode")).getSelectionModel().select(1);
                ((TextField) scene.lookup("#auction-create-max-participants")).setText("25");
                ((TextField) scene.lookup("#auction-create-deposit")).setText("50000");
                ((DatePicker) scene.lookup("#auction-start-date")).setValue(java.time.LocalDate.now().minusDays(1));
                dialogButton(ButtonBar.ButtonData.OK_DONE).fire();
                TestSupport.check(scene.lookup("#auction-create-error").isVisible(), "past start rejected inline and form stays open");
                var day = (DatePicker) scene.lookup("#auction-start-date"); day.setValue(java.time.LocalDate.now().plusDays(1));
                @SuppressWarnings("unchecked") var hour = (ComboBox<String>) scene.lookup("#auction-start-hour"); hour.setValue("14");
                @SuppressWarnings("unchecked") var minute = (ComboBox<String>) scene.lookup("#auction-start-minute"); minute.setValue("30");
                return null;
            });
            screenshot(fx(() -> dialogButton(ButtonBar.ButtonData.OK_DONE).getScene().getRoot()), "target/auction-create-scheduled.png");
            fx(() -> { dialogButton(ButtonBar.ButtonData.OK_DONE).fire(); return null; }); drain();
            TestSupport.equals(0, network.count(MessageType.CREATE_AUCTION), "review step never submits before confirmation");
            fx(() -> { dialogButton(ButtonBar.ButtonData.OK_DONE).fire(); return null; }); drain();
            TestSupport.equals(1, network.count(MessageType.CREATE_AUCTION), "review confirmation sends exactly one request");
            var sent = network.requests.stream().filter(value -> value.type() == MessageType.CREATE_AUCTION).findFirst().orElseThrow();
            TestSupport.equals("25", sent.data().get("maxParticipants"), "form submits mandatory capacity");
            TestSupport.equals("50000", sent.data().get("depositAmount"), "scheduled form submits deposit");
            TestSupport.equals(java.time.LocalDate.now().plusDays(1).atTime(14, 30).atZone(java.time.ZoneId.systemDefault()).toInstant().toString(),
                    sent.data().get("startTime"), "scheduled form sends exact local date and time as UTC");
            fx(() -> { invoke(controller, "showCreateAuction", new Class<?>[0]); return null; });
            TestSupport.equals(0, network.count(MessageType.MY_PRODUCTS), "pending create prevents second dialog/request");
            var created = new LinkedHashMap<>(sent.data());
            created.putAll(Map.of("auctionId", "101", "hostUserId", "2", "hostUsername", "test", "productName", "Scheduled test product",
                    "currentPrice", "100000", "status", "OPEN", "version", "1",
                    "endTime", java.time.Instant.parse(sent.data().get("startTime")).plusSeconds(300).toString()));
            network.take(MessageType.CREATE_AUCTION).reply(created); drain();
            fx(() -> {
                var scene = ((Stage) field(controller, "stage")).getScene();
                TestSupport.check(((ToggleButton) scene.lookup("#marketplace-phase-upcoming")).isSelected(), "creating scheduled room automatically opens upcoming tab");
                TestSupport.check(((AuctionMarketplacePane) field(controller, "auctionCards")).getItems().stream().anyMatch(room -> room.getAuctionId() == 101), "new scheduled room appears in upcoming after acknowledgement");
                return null;
            });
            fx(() -> { Stage stage = (Stage) field(controller, "stage"); stage.setWidth(940); stage.setHeight(650); return null; }); drain();
            screenshot(fx(() -> ((Stage) field(controller, "stage")).getScene().getRoot()), "target/completion-lobby-small.png");
            fx(() -> { controller.close(); return null; });
            congratulations();
            System.out.println("[PASS] CompletionUiSelfTest: persisted inbox dedup/session gate, wallet filter stale callbacks, old-server schedule guard, scheduled form validation/UTC, nearest-start order and automatic upcoming-to-live transition, grouped creation/review/back with retained draft, private password validation/privacy, modal blur cleanup, short-window controls and duplicate-submit guard, screenshots");
        } finally { fx(() -> { for (Window window : List.copyOf(Window.getWindows())) { if (window instanceof Stage stage) { stage.close(); } } return null; }); Platform.exit(); }
    }
    private static void congratulations() throws Exception {
        var marquee = fx(() -> {
            var bar = new AuctionCongratulations(12000);
            var root = new javafx.scene.layout.VBox(bar); Stage stage = new Stage();
            var scene = new Scene(root, 720, 100); vn.ptit.btl16.common.fx.DesktopTheme.apply(scene); stage.setScene(scene); stage.show();
            return bar;
        });
        fx(() -> {
            Map<String, String> result = Map.of("auctionId", "101", "winnerId", "2", "winnerUsername", "Alice", "productName", "Tai nghe", "finalPrice", "1500000");
            TestSupport.check(marquee.announce(result), "new global result shown");
            TestSupport.check(!marquee.announce(result), "duplicate global event does not repeat result");
            TestSupport.check(((Label) marquee.lookup("#auction-congratulations-text")).getText().contains("Alice (ID 2)")
                    && ((Label) marquee.lookup("#auction-congratulations-text")).getText().contains("1.500.000"), "winner, product and exact winning price shown");
            TestSupport.check(marquee.announce(Map.of("auctionId", "102", "winnerId", "-3", "winnerUsername", "Khách #3", "productName", "Loa", "finalPrice", "2000000")), "concurrent result queued with public anonymous alias");
            TestSupport.check(!marquee.announce(Map.of("auctionId", "103", "winnerId", "", "finalPrice", "0")), "no-winner result does not congratulate");
            return null;
        });
        fx(() -> {
            var motion = (javafx.animation.TranslateTransition) field(marquee, "motion");
            TestSupport.check(motion.getFromX() > 0 && motion.getToX() < 0, "marquee moves from right to left"); return null;
        });
        TestSupport.waitUntil(() -> { try { return fx(() -> !marquee.isManaged()); } catch (Exception error) { throw new RuntimeException(error); } }, 5000, "all queued announcements complete");
        TestSupport.equals(3, fx(() -> field(marquee, "passes")), "each announcement finishes after three passes");
        fx(() -> { marquee.close(); ((Stage) marquee.getScene().getWindow()).close(); return null; });
    }
    private static Map<String,String> product() { return Map.of("productCount", "1", "product.0.productId", "1", "product.0.ownerId", "2", "product.0.code", "CODE", "product.0.name", "Test product", "product.0.active", "true"); }
    private static void eligibilityNotice(FxClientController controller, Network network) throws Exception {
        Stage owner = fx(() -> (Stage) field(controller, "stage"));
        fx(() -> { invoke(controller, "showCreateAuction", new Class<?>[0]); return null; });
        network.take(MessageType.MY_PRODUCTS).reply(Map.of("productCount", "0")); drain();
        Stage notice = fx(CompletionUiSelfTest::eligibilityWindow);
        fx(() -> {
            TestSupport.check(owner.getScene().lookup("#auction-eligibility-backdrop") != null, "eligibility notice dims the background");
            TestSupport.check(((javafx.scene.layout.StackPane) owner.getScene().getRoot()).getChildren().getFirst().getEffect() instanceof javafx.scene.effect.GaussianBlur, "eligibility notice blurs the background");
            TestSupport.check(notice.getScene().lookup("#auction-eligibility-artwork") != null, "eligibility notice contains its auction illustration");
            TestSupport.check(notice.getHeight() < owner.getHeight() * .8, "eligibility card stays compact instead of stretching to the screen height");
            TestSupport.check(Math.abs(notice.getX() + notice.getWidth() / 2 - owner.getX() - owner.getWidth() / 2) < 4
                    && Math.abs(notice.getY() + notice.getHeight() / 2 - owner.getY() - owner.getHeight() / 2) < 4,
                    "eligibility card is centered: owner=" + owner.getX() + "," + owner.getY() + "," + owner.getWidth() + "," + owner.getHeight()
                            + " notice=" + notice.getX() + "," + notice.getY() + "," + notice.getWidth() + "," + notice.getHeight());
            invoke(controller, "showCreateAuction", new Class<?>[0]);
            TestSupport.equals(0, network.count(MessageType.MY_PRODUCTS), "open eligibility notice prevents duplicate catalog requests"); return null;
        });
        screenshot(notice.getScene().getRoot(), "target/auction-eligibility-dialog.png");
        screenshot(owner.getScene().getRoot(), "target/auction-eligibility-background.png");
        fx(() -> { ((Button) notice.getScene().lookup("#auction-eligibility-close")).fire(); return null; }); drain();
        fx(() -> {
            TestSupport.check(owner.getScene().lookup("#auction-eligibility-backdrop") == null && ((javafx.scene.layout.StackPane) owner.getScene().getRoot()).getChildren().getFirst().getEffect() == null, "X restores the background and removes the blur");
            TestSupport.check(!((Boolean) field(controller, "creatingAuction")), "X releases the create-room guard");
            invoke(controller, "showCreateAuction", new Class<?>[0]); return null;
        });
        var sold = new LinkedHashMap<>(product()); sold.put("product.0.transactionStatus", "SOLD");
        network.take(MessageType.MY_PRODUCTS).reply(sold); drain();
        fx(() -> { ((Button) eligibilityWindow().getScene().lookup("#auction-eligibility-retry")).fire(); return null; }); drain();
        TestSupport.equals(1, network.count(MessageType.MY_PRODUCTS), "retry reloads the catalog exactly once");
        network.take(MessageType.MY_PRODUCTS).reply(Map.of("productCount", "0")); drain();
        fx(() -> { ((Button) eligibilityWindow().getScene().lookup("#auction-eligibility-close")).fire(); return null; }); drain();
    }
    private static Stage eligibilityWindow() {
        return (Stage) Window.getWindows().stream().filter(window -> window.isShowing() && window.getScene() != null && window.getScene().lookup("#auction-eligibility-dialog") != null).findFirst().orElseThrow();
    }
    private static Map<String,String> notifications(String kind) { return Map.of("count", "1", "hasMore", "false", "nextBeforeId", "1", "unreadCount", "1", "notification.0.id", "1", "notification.0.kind", kind, "notification.0.auctionId", "2", "notification.0.reference", "", "notification.0.createdAt", "2026-10-25T10:00:00Z", "notification.0.read", "false"); }
    private static Map<String,String> ledger(String reference) {
        var result = new LinkedHashMap<>(Map.of("userId", "2", "entryCount", "1", "hasMore", "false", "nextBeforeId", "1"));
        result.putAll(Map.of("balance", "900", "available", "900"));
        result.putAll(Map.of("entry.0.id", "1", "entry.0.type", "PURCHASE", "entry.0.amount", "-100", "entry.0.reservedDelta", "-100", "entry.0.balanceAfter", "900", "entry.0.reservedAfter", "0", "entry.0.reference", reference, "entry.0.createdAt", "2026-10-25T10:00:00Z")); return result;
    }
    private static Button dialogButton(ButtonBar.ButtonData data) {
        for (Window window : List.copyOf(Window.getWindows())) {
            if (!window.isShowing() || window.getScene() == null) { continue; }
            var root = window.getScene().getRoot(); DialogPane pane = root instanceof DialogPane dialog ? dialog : (DialogPane) root.lookup(".dialog-pane");
            if (pane != null) { for (var type : pane.getButtonTypes()) { if (type.getButtonData() == data) { return (Button) pane.lookupButton(type); } } }
        }
        throw new AssertionError("No dialog button " + data);
    }
    private static void screenshot(javafx.scene.Node node, String path) throws Exception { fx(() -> { node.applyCss(); if (node instanceof javafx.scene.Parent parent) { parent.layout(); } var image = node.snapshot(null, null); var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB); for (int row = 0; row < output.getHeight(); row++) { for (int column = 0; column < output.getWidth(); column++) { output.setRGB(column, row, image.getPixelReader().getArgb(column, row)); } } javax.imageio.ImageIO.write(output, "png", new java.io.File(path)); return null; }); }
    private static Object field(Object target, String name) throws Exception { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static TableView<?> table(Object target, String name) throws Exception { return (TableView<?>) field(target, name); }
    private static void invoke(Object target, String name, Class<?>[] types, Object... values) throws Exception { Method method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(target, values); }
    private static <Result> Result fx(Callable<Result> action) throws Exception { if (Platform.isFxApplicationThread()) { return action.call(); } var result = new CompletableFuture<Result>(); Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } }); return result.get(15, TimeUnit.SECONDS); }
    private static void drain() throws Exception { fx(() -> null); fx(() -> null); }
    private static final class Network implements ClientTransport {
        private final List<Pending> requests = new ArrayList<>();
        boolean supportsScheduling = true;
        synchronized int count(MessageType type) { return (int) requests.stream().filter(value -> value.type() == type).count(); }
        synchronized Pending take(MessageType type) { var value = requests.stream().filter(item -> item.type() == type).findFirst().orElseThrow(() -> new AssertionError("Missing " + type)); requests.remove(value); return value; }
        @Override public synchronized CompletableFuture<WireMessage> sendRequest(MessageType type, Map<String,String> data) {
            if (type == MessageType.PING) { return CompletableFuture.completedFuture(WireMessage.response(MessageType.PONG, null, 1,
                    Map.of("success", "true", "auctionScheduling", Boolean.toString(supportsScheduling), "roomAdmission", "true"))); }
            if (Set.of(MessageType.GET_MY_NOTIFICATIONS, MessageType.GET_WALLET_ENTRIES, MessageType.MY_PRODUCTS, MessageType.CREATE_AUCTION).contains(type)) { var future = new CompletableFuture<WireMessage>(); requests.add(new Pending(type, Map.copyOf(data), future)); return future; }
            return CompletableFuture.completedFuture(WireMessage.response(type, null, 1, Map.of("success", "true", "userId", "2", "balance", "1000", "reserved", "0", "available", "1000", "requestCount", "0", "entryCount", "0", "memberCount", "0", "chatCount", "0")));
        }
        @Override public CompletableFuture<Void> connect() { return CompletableFuture.completedFuture(null); }
        @Override public void disconnect() { }
        @Override public boolean isConnected() { return true; }
        @Override public ConnectionState getState() { return ConnectionState.CONNECTED; }
        @Override public String getEndpoint() { return "test"; }
        @Override public void setEndpoint(String endpoint) { }
        @Override public CompletableFuture<WireMessage> ping() { return sendRequest(MessageType.PING, Map.of()); }
        @Override public void addEventListener(Consumer<WireMessage> listener) { }
        @Override public void addStateListener(ConnectionStateListener listener) { }
        @Override public void close() { }
    }
}
