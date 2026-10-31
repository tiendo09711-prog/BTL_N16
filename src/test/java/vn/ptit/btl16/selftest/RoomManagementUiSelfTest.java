package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import vn.ptit.btl16.client.fx.*;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class RoomManagementUiSelfTest {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        var config = ServerConfig.forTests(0, 3, 3, 1); var hasher = new Pbkdf2PasswordHasher(config.getPasswordIterations());
        try (var server = ServerApplication.createForTests(config, TestUserRepository.withDemoUsers(hasher), TestAuctionRepository.withDemoAuctions(config), hasher)) {
            server.start();
            try (var host = RoomInteractionSelfTest.client(server.getBoundPort()); var alice = RoomInteractionSelfTest.client(server.getBoundPort());
                    var bob = RoomInteractionSelfTest.client(server.getBoundPort()); var extra = RoomInteractionSelfTest.client(server.getBoundPort());
                    var viewer = RoomInteractionSelfTest.client(server.getBoundPort())) {
                ok(new AccountApi(host).login("demo", "demo123")); ok(new AccountApi(alice).login("alice", "alice123")); ok(new AccountApi(bob).login("bob", "bob123"));
                ok(new AccountApi(extra).register("moderation_extra", "Password123!", "Extra Guest", "", ""));
                ok(new AccountApi(extra).login("moderation_extra", "Password123!"));
                ok(new AccountApi(viewer).register("moderation_viewer", "Password123!", "Other Viewer", "", ""));
                ok(new AccountApi(viewer).login("moderation_viewer", "Password123!"));
                Stage owner = fx(() -> { Stage stage = new Stage(); Scene scene = new Scene(new VBox(), 1000, 720); DesktopTheme.apply(scene); stage.setScene(scene); stage.show(); return stage; });
                try {
                    announcement(owner);
                    for (boolean anonymous : List.of(false, true)) {
                        batch(owner, host, alice, bob, extra, viewer, anonymous);
                    }
                } finally { fx(() -> { owner.close(); return null; }); }
            }
            System.out.println("[PASS] RoomManagementUiSelfTest: normal/anonymous multi-select, search retention, protected bidders, partial batch race, stale/offline guards, pin validation, responsive history and screenshots");
        } finally { Platform.exit(); }
    }

    private static void announcement(Stage owner) throws Exception {
        RoomAnnouncementDialog dialog = fx(() -> { var editor = new RoomAnnouncementDialog(owner, ""); editor.show(); return editor; });
        fx(() -> {
            TextArea text = (TextArea) dialog.getDialogPane().lookup("#room-announcement-body");
            TestSupport.check(((Label) dialog.getDialogPane().lookup(".room-dialog-title")).getFont().getSize() <= 13, "announcement prompt uses regular text size");
            Button save = (Button) dialog.getDialogPane().lookup("#room-announcement-save");
            TestSupport.check(save.isDisabled(), "blank pin cannot be saved");
            text.setText("\ud83d\udc4b".repeat(500)); TestSupport.check(!save.isDisabled(), "500 Unicode code points allowed");
            text.appendText("x"); TestSupport.check(save.isDisabled(), "501 code points rejected before submit");
            text.setText("Loi chao va quy dinh cua phien dau gia");
            snapshot(dialog.getDialogPane().getScene(), "announcement-editor"); dialog.close(); return null;
        });
    }

    private static void batch(Stage owner, NetworkClient host, NetworkClient alice, NetworkClient bob,
            NetworkClient extra, NetworkClient viewer, boolean anonymous) throws Exception {
        AuctionApi hostApi = new AuctionApi(host), aliceApi = new AuctionApi(alice), extraApi = new AuctionApi(extra);
        long room = RoomInteractionSelfTest.create(hostApi, anonymous ? "MANAGEMENT_ANON" : "MANAGEMENT_NORMAL", anonymous);
        var hostJoin = ok(hostApi.join(room)); var aliceJoin = ok(aliceApi.join(room));
        ok(new AuctionApi(bob).join(room)); ok(extraApi.join(room)); ok(new AuctionApi(viewer).join(room));
        long aliceId = Long.parseLong(aliceJoin.get("selfParticipantId"));
        history(owner, host, room, anonymous, 0);
        ok(aliceApi.bid(room, new BigDecimal("110000")));
        pinPreview(owner, host, room, hostJoin, anonymous);
        RecordingTransport transport = new RecordingTransport(host); AtomicBoolean current = new AtomicBoolean(true);
        RoomParticipantsDialog dialog = fx(() -> {
            var picker = new RoomParticipantsDialog(owner, transport, room, current::get, () -> { }, null); picker.show(); return picker;
        });
        await(() -> ((ListView<?>) dialog.getDialogPane().lookup("#room-kick-list")).getItems().size() == 4
                && !dialog.getDialogPane().lookup("#room-kick-select-all").isDisabled(), "fresh member list");
        fx(() -> {
            DialogPane pane = dialog.getDialogPane();
            TestSupport.check(!((Button) pane.lookup("#room-kick-submit")).isDefaultButton(), "Enter in search must not remove selected guests");
            pane.applyCss(); pane.layout();
            CheckBox protectedBidder = (CheckBox) pane.lookup("#room-kick-check-" + aliceId);
            TestSupport.check(protectedBidder != null && protectedBidder.isDisabled(), "accepted bidder shown but not selectable");
            String labels = labels(pane.getScene());
            if (anonymous) {
                TestSupport.check(!labels.contains("Alice") && !labels.contains("Bob") && !labels.contains("Extra Guest") && !labels.contains("Other Viewer"), "anonymous picker never exposes real names");
            } else { TestSupport.check(labels.contains("Alice") && labels.contains("Bob"), "normal picker uses display names"); }
            ((CheckBox) pane.lookup("#room-kick-select-all")).fire();
            TestSupport.check(((Label) pane.lookup("#room-kick-count")).getText().contains("3"), "select all selects only eligible guests");
            TextField search = (TextField) pane.lookup("#room-kick-search"); search.setText("NO_MATCH");
            TestSupport.equals(0, ((ListView<?>) pane.lookup("#room-kick-list")).getItems().size(), "filter displays empty state");
            TestSupport.check(!pane.lookup("#room-kick-submit").isDisabled(), "selection retained when filtered out");
            search.clear(); snapshot(pane.getScene(), anonymous ? "participants-anonymous" : "participants-normal");
            current.set(false); ((Button) pane.lookup("#room-kick-submit")).fire();
            TestSupport.equals(0, transport.kicks.size(), "stale dialog cannot remove anyone"); current.set(true);
            transport.online = false; ((Button) pane.lookup("#room-kick-submit")).fire();
            TestSupport.equals(0, transport.kicks.size(), "offline dialog cannot remove anyone"); transport.online = true;
            return null;
        });
        ok(extraApi.bid(room, new BigDecimal("120000")));
        fx(() -> { ((Button) dialog.getDialogPane().lookup("#room-kick-submit")).fire(); return null; });
        await(() -> transport.kicks.size() == 3 && !dialog.getDialogPane().lookup("#room-kick-reload").isDisabled(), "batch with raced bidder completes");
        await(() -> ((ListView<?>) dialog.getDialogPane().lookup("#room-kick-list")).getItems().size() == 2, "successful removals leave two protected bidders");
        fx(() -> {
            DialogPane pane = dialog.getDialogPane();
            TestSupport.check(((Label) pane.lookup("#room-kick-status")).getText().contains("2"), "partial batch reports confirmed successes");
            TestSupport.check(pane.lookup("#room-kick-submit").isDisabled(), "new bidder is no longer eligible on refresh");
            TestSupport.check(transport.kicks.stream().allMatch(data -> !data.containsKey("username")), "batch uses participant IDs, never typed usernames");
            if (anonymous) { TestSupport.check(transport.kicks.stream().allMatch(data -> Long.parseLong(data.get("participantId")) < 0), "anonymous kick uses room aliases only"); }
            snapshot(pane.getScene(), anonymous ? "participants-result-anonymous" : "participants-result-normal");
            dialog.close(); return null;
        });
        TestSupport.equals("USER_BLOCKED_FROM_AUCTION", new AuctionApi(bob).join(room).get(10, TimeUnit.SECONDS).getErrorCode(), "removed guest cannot rejoin");
        TestSupport.equals("USER_BLOCKED_FROM_AUCTION", new AuctionApi(viewer).join(room).get(10, TimeUnit.SECONDS).getErrorCode(), "second removed guest cannot rejoin");
        history(owner, host, room, anonymous, 2);
        ok(aliceApi.leave(room)); ok(extraApi.leave(room)); ok(hostApi.leave(room));
    }

    private static void history(Stage owner, ClientTransport transport, long room, boolean anonymous, int expected) throws Exception {
        BidHistoryPane pane = fx(() -> new BidHistoryPane(transport, room, () -> true));
        Dialog<Void> dialog = fx(() -> {
            Dialog<Void> window = new Dialog<>(); window.getDialogPane().getStyleClass().addAll("room-dialog", "room-history-dialog");
            window.getDialogPane().setPrefWidth(860); window.getDialogPane().setContent(pane);
            window.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            ((Button) window.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("\u0110\u00f3ng");
            DesktopTheme.dialog(window, owner); window.show(); return window;
        });
        Stage stage = fx(() -> (Stage) dialog.getDialogPane().getScene().getWindow());
        try {
            await(() -> ((TableView<?>) pane.lookup("#bid-history-table")).getItems().size() == expected
                    && !pane.lookup("#bid-history-newest").isDisabled(), "full history loads accepted bids or empty state");
            fx(() -> {
                pane.applyCss(); pane.layout();
                TestSupport.check(pane.lookup("#bid-history-table").isVisible(), "desktop uses four-column table");
                if (anonymous) { TestSupport.check(!labels(stage.getScene()).contains("Alice") && !labels(stage.getScene()).contains("Extra Guest"), "history also preserves anonymity"); }
                snapshot(stage.getScene(), (anonymous ? "history-anonymous" : "history-desktop") + (expected == 0 ? "-empty" : "")); stage.setWidth(510); return null;
            });
            await(() -> !pane.lookup("#bid-history-table").isManaged(), "narrow history switches to readable cards");
            fx(() -> { snapshot(stage.getScene(), (anonymous ? "history-anonymous-compact" : "history-compact") + (expected == 0 ? "-empty" : "")); return null; });
        } finally { fx(() -> { pane.dispose(); dialog.close(); return null; }); }
    }

    private static void pinPreview(Stage owner, ClientTransport transport, long room, ApiResponse joined, boolean anonymous) throws Exception {
        String body = "Loi chao tu chu phong. Vui long giu lich su trong khi trao doi, doc ky thong tin san pham va kiem tra muc gia truoc khi gui. ".repeat(3).strip();
        ok(new RoomSocialApi(transport).announcement(room, body, UUID.randomUUID().toString()));
        RoomSocialPane pane = fx(() -> new RoomSocialPane(transport));
        Stage stage = fx(() -> {
            Stage window = new Stage(); window.initOwner(owner);
            ScrollPane scroll = new ScrollPane(pane); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.getStyleClass().add("room-bid-scroll");
            Scene scene = new Scene(scroll, 400, 720); DesktopTheme.apply(scene); window.setScene(scene); window.show();
            pane.setOwner(window); pane.setRoom(ClientWireParser.auction(joined.getWireMessage().getData(), ""), 1); return window;
        });
        try {
            await(() -> ((Label) pane.lookup("#room-announcement")).getText().startsWith("Loi chao"), "pinned card loads");
            fx(() -> {
                Label announcement = (Label) pane.lookup("#room-announcement");
                TestSupport.check(announcement.getText().length() < body.length(), "long pin starts collapsed");
                ((Button) pane.lookup("#room-announcement-expand")).fire();
                TestSupport.equals(body, announcement.getText(), "expand reads entire pin without a tooltip");
                ((Button) pane.lookup("#room-announcement-expand")).fire();
                snapshot(stage.getScene(), anonymous ? "chat-pinned-anonymous" : "chat-pinned-normal");
                TestSupport.check(pane.lookup("#room-announcement-edit").getBoundsInParent().getWidth() > 40, "host edit action remains readable in narrow chat");
                return null;
            });
        } finally { fx(() -> { pane.clear(); stage.close(); return null; }); }
    }

    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception { return RoomInteractionSelfTest.ok(future); }
    private static String labels(Scene scene) { return scene.getRoot().lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast).map(Label::getText).collect(java.util.stream.Collectors.joining(" ")); }
    private static void await(Callable<Boolean> condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) { if (fx(condition)) { return; } Thread.sleep(40); } throw new AssertionError("Timeout: " + message);
    }
    private static <Result> Result fx(Callable<Result> action) throws Exception {
        var result = new CompletableFuture<Result>(); Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } }); return result.get(20, TimeUnit.SECONDS);
    }
    private static void snapshot(Scene scene, String name) throws Exception {
        scene.getRoot().applyCss(); scene.getRoot().layout(); var image = scene.snapshot(null);
        BufferedImage bitmap = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < bitmap.getHeight(); row++) { for (int column = 0; column < bitmap.getWidth(); column++) { bitmap.setRGB(column, row, image.getPixelReader().getArgb(column, row)); } }
        Path folder = Path.of("target", "room-management-preview"); Files.createDirectories(folder); ImageIO.write(bitmap, "png", folder.resolve(name + ".png").toFile());
    }

    private static final class RecordingTransport implements ClientTransport {
        private final ClientTransport delegate;
        private final List<Map<String, String>> kicks = new CopyOnWriteArrayList<>();
        private boolean online = true;
        RecordingTransport(ClientTransport delegate) { this.delegate = delegate; }
        @Override public CompletableFuture<WireMessage> sendRequest(MessageType type, Map<String, String> data) {
            if (type == MessageType.KICK_AUCTION_USER) { kicks.add(Map.copyOf(data)); } return delegate.sendRequest(type, data);
        }
        @Override public boolean isConnected() { return online && delegate.isConnected(); }
        @Override public CompletableFuture<Void> connect() { return delegate.connect(); }
        @Override public void disconnect() { delegate.disconnect(); }
        @Override public ConnectionState getState() { return delegate.getState(); }
        @Override public String getEndpoint() { return delegate.getEndpoint(); }
        @Override public void setEndpoint(String endpoint) { delegate.setEndpoint(endpoint); }
        @Override public CompletableFuture<WireMessage> ping() { return delegate.ping(); }
        @Override public void addEventListener(Consumer<WireMessage> listener) { delegate.addEventListener(listener); }
        @Override public void addStateListener(ConnectionStateListener listener) { delegate.addStateListener(listener); }
        @Override public void close() { }
    }
}
