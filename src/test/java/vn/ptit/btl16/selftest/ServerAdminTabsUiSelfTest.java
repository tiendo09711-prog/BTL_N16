package vn.ptit.btl16.selftest;

import javafx.animation.Animation;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.common.util.RevenuePeriod;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.fx.ServerFxApp;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class ServerAdminTabsUiSelfTest {
    private ServerAdminTabsUiSelfTest() { }

    public static void main(String[] args) throws Exception {
        Path data = Files.createTempDirectory("btl16-admin-tabs-");
        Path config = data.resolve("server.properties");
        Files.writeString(config, "server.tcp.enabled=false\nserver.websocket.port=0\nmedia.port=0\n"
                + "db.driver=org.h2.Driver\ndb.name=auction\ndb.user=sa\ndb.password=\nsecurity.passwordIterations=10000\n");
        System.setProperty("btl16.server.config", config.toString());
        System.setProperty("btl16.dataDir", data.toString());
        Platform.startup(() -> Platform.setImplicitExit(false));
        ServerFxApp app = new ServerFxApp();
        Stage stage = fx(() -> { Stage window = new Stage(); app.start(window); return window; });
        try {
            waitFor(() -> field(app, "application") != null && !(boolean) field(app, "busy"), "server starts");
            ServerApplication server = fx(() -> (ServerApplication) field(app, "application"));
            long userId;
            try (var client = new WebSocketClientTransport("ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws", 2097152, 5000, 10000)) {
                client.connect().get(10, TimeUnit.SECONDS);
                AccountApi accounts = new AccountApi(client);
                TestSupport.check(accounts.register("admin_tabs", "Password123!", "Admin Tabs", "tabs@example.test", "0900000000").get(10, TimeUnit.SECONDS).isSuccess(), "create real account");
                var identity = accounts.login("admin_tabs", "Password123!").get(10, TimeUnit.SECONDS);
                TestSupport.check(identity.isSuccess(), "login to obtain account id");
                userId = Long.parseLong(identity.get("userId"));
            }
            String funding = UUID.randomUUID().toString();
            server.wallets().submit(userId, funding, new BigDecimal("30000"));
            server.wallets().review(funding, true);
            server.wallets().submit(userId, UUID.randomUUID().toString(), new BigDecimal("1000"));
            server.wallets().submitWithdrawal(userId, UUID.randomUUID().toString(), new BigDecimal("2000"));

            TabPane mainTabs = fx(() -> (TabPane) stage.getScene().lookup("#server-admin-tabs"));
            fx(() -> {
                TestSupport.equals(4, mainTabs.getTabs().size(), "all four admin workspaces exist");
                TestSupport.equals(TabPane.TabClosingPolicy.UNAVAILABLE, mainTabs.getTabClosingPolicy(), "admin sections cannot be accidentally closed");
                return null;
            });
            snapshot(stage, "overview");
            navigate(stage, "server-wallet-open", 1);
            Object wallet = fx(() -> mainTabs.getSelectionModel().getSelectedItem().getContent());
            waitFor(() -> ((TableView<?>) field(wallet, "accounts")).getItems().size() == 1
                    && ((TableView<?>) field(wallet, "pending")).getItems().size() == 1
                    && ((TableView<?>) field(wallet, "pendingWithdrawals")).getItems().size() == 1, "accounts and both review queues load inside main window");
            fx(() -> { ((TableView<?>) field(wallet, "accounts")).getSelectionModel().selectFirst(); return null; });
            waitFor(() -> ((Label) field(wallet, "profile")).getText().contains("tabs@example.test")
                    && !((TableView<?>) field(wallet, "entries")).getItems().isEmpty(), "account profile and ledger load");
            snapshot(stage, "accounts");
            fx(() -> {
                Label profile = (Label) field(wallet, "profile");
                TestSupport.check(profile.getHeight() + 1 >= profile.prefHeight(profile.getWidth()), "full account profile fits without clipping");
                double historyHeight = ((TableView<?>) field(wallet, "userRequests")).getHeight();
                TestSupport.check(historyHeight >= 100, "account history has room for rows, not just column headers: " + historyHeight);
                return null;
            });
            TabPane walletTabs = fx(() -> (TabPane) stage.getScene().lookup("#server-wallet-tabs"));
            fx(() -> {
                TestSupport.equals(3, walletTabs.getTabs().size(), "accounts, topups and withdrawals are all reachable");
                TabPane history = (TabPane) stage.getScene().lookup("#server-wallet-history-tabs");
                TestSupport.equals(3, history.getTabs().size(), "deposit, withdrawal and ledger histories all remain available");
                history.getSelectionModel().select(2);
                walletTabs.getSelectionModel().select(1);
                return null;
            });
            snapshot(stage, "topups");
            fx(() -> { walletTabs.getSelectionModel().select(2); ((TextField) field(wallet, "withdrawalRejectionReason")).setText("Keep this review reason"); return null; });
            snapshot(stage, "withdrawals");
            Button approval = fx(() -> {
                ((TableView<?>) field(wallet, "pendingWithdrawals")).getSelectionModel().selectFirst();
                return (Button) field(wallet, "approveWithdrawal");
            });
            Platform.runLater(approval::fire);
            waitFor(() -> showingDialog() != null, "approval still asks for explicit confirmation");
            fx(() -> {
                DialogPane dialog = showingDialog();
                TestSupport.equals(stage, ((Stage) dialog.getScene().getWindow()).getOwner(), "confirmation belongs to the main server window");
                ((Button) dialog.lookupButton(ButtonType.NO)).fire();
                return null;
            });
            TestSupport.equals(new BigDecimal("30000.00"), server.wallets().account(userId).balance().setScale(2), "cancelling confirmation never debits money");
            Button topUp = fx(() -> {
                walletTabs.getSelectionModel().select(1);
                ((TableView<?>) field(wallet, "pending")).getSelectionModel().selectFirst();
                return (Button) field(wallet, "approve");
            });
            Platform.runLater(topUp::fire); confirmYes();
            waitFor(() -> ((TableView<?>) field(wallet, "pending")).getItems().isEmpty(), "embedded top-up approval refreshes queue");
            TestSupport.equals(new BigDecimal("31000.00"), server.wallets().account(userId).balance().setScale(2), "embedded top-up credits exactly once");
            fx(() -> { walletTabs.getSelectionModel().select(2); ((TableView<?>) field(wallet, "pendingWithdrawals")).getSelectionModel().selectFirst(); return null; });
            Platform.runLater(approval::fire); confirmYes();
            waitFor(() -> ((TableView<?>) field(wallet, "pendingWithdrawals")).getItems().isEmpty(), "embedded withdrawal approval refreshes queue");
            TestSupport.equals(new BigDecimal("29000.00"), server.wallets().account(userId).balance().setScale(2), "embedded withdrawal debits exactly once");

            navigate(stage, "server-cash-flow-open", 2);
            Object cash = fx(() -> mainTabs.getSelectionModel().getSelectedItem().getContent());
            waitFor(() -> !((Label) field(cash, "status")).getText().startsWith("\u0110ang t\u1ea3i"), "cash flow loads");
            fx(() -> {
                @SuppressWarnings("unchecked") ComboBox<RevenuePeriod> period = (ComboBox<RevenuePeriod>) field(cash, "period");
                period.setValue(RevenuePeriod.ONE_YEAR);
                TestSupport.equals(Animation.Status.STOPPED, ((Timeline) field(wallet, "poll")).getStatus(), "hidden wallet stops polling");
                TestSupport.equals(null, field(wallet, "stage"), "embedded wallet never allocates a secondary stage");
                TestSupport.equals(null, field(cash, "stage"), "embedded cash flow never allocates a secondary stage");
                return null;
            });
            waitFor(() -> !(boolean) field(cash, "loading"), "annual cash-flow filter completes");
            snapshot(stage, "cash-flow");
            navigate(stage, "server-operations-open", 3);
            Object logs = fx(() -> mainTabs.getSelectionModel().getSelectedItem().getContent());
            waitFor(() -> !(boolean) field(logs, "loading"), "operation log loads");
            fx(() -> {
                ((TextField) field(logs, "type")).setText("BID_ACCEPTED");
                ((Button) stage.getScene().lookup("#server-log-filter")).fire();
                TestSupport.equals(Animation.Status.STOPPED, ((Timeline) field(cash, "poll")).getStatus(), "hidden cash flow stops polling");
                TestSupport.equals(null, field(logs, "stage"), "embedded log never allocates a secondary stage");
                return null;
            });
            waitFor(() -> !(boolean) field(logs, "loading"), "log filtering completes");
            snapshot(stage, "operations");

            for (int repeat = 0; repeat < 3; repeat++) {
                navigate(stage, "server-overview-open", 0);
                navigate(stage, "server-wallet-open", 1);
                fx(() -> {
                    TestSupport.check(wallet == mainTabs.getSelectionModel().getSelectedItem().getContent(), "wallet page is cached, not rebuilt");
                    TestSupport.equals(2, walletTabs.getSelectionModel().getSelectedIndex(), "wallet subtab survives navigation");
                    TestSupport.equals("Keep this review reason", ((TextField) field(wallet, "withdrawalRejectionReason")).getText(), "review draft survives navigation");
                    return null;
                });
                navigate(stage, "server-cash-flow-open", 2);
                fx(() -> { TestSupport.equals(RevenuePeriod.ONE_YEAR, ((ComboBox<?>) field(cash, "period")).getValue(), "cash-flow period survives navigation"); return null; });
                navigate(stage, "server-operations-open", 3);
                fx(() -> { TestSupport.equals("BID_ACCEPTED", ((TextField) field(logs, "type")).getText(), "log filter survives navigation"); return null; });
            }

            ExecutorService worker = fx(() -> (ExecutorService) field(app, "worker"));
            CountDownLatch release = new CountDownLatch(1);
            worker.execute(() -> { try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); } });
            try {
                navigate(stage, "server-wallet-open", 1);
                navigate(stage, "server-operations-open", 3);
            } finally { release.countDown(); }
            navigate(stage, "server-wallet-open", 1);
            waitFor(() -> !(boolean) field(wallet, "loading"), "wallet resumes after a response arrives while hidden");

            fx(() -> { stage.setWidth(980); stage.setHeight(680); return null; });
            fx(() -> { mainTabs.getSelectionModel().select(1); walletTabs.getSelectionModel().selectFirst(); return null; });
            snapshot(stage, "compact-accounts");
            for (int index = 0; index < 4; index++) {
                final int selected = index;
                fx(() -> { mainTabs.getSelectionModel().select(selected); return null; });
                snapshot(stage, "compact-" + index);
                fx(() -> {
                    Node content = mainTabs.getSelectionModel().getSelectedItem().getContent();
                    TestSupport.check(content.getBoundsInParent().getWidth() > 600, "compact page remains usable");
                    String[] buttons = {"server-overview-open", "server-wallet-open", "server-cash-flow-open", "server-operations-open"};
                    TestSupport.check(stage.getScene().lookup("#" + buttons[selected]).getStyleClass().contains("nav-active"), "selecting a tab updates the matching sidebar item");
                    TestSupport.equals(1L, Window.getWindows().stream().filter(Window::isShowing).count(), "compact navigation stays in one window");
                    return null;
                });
            }
            System.out.println("[PASS] ServerAdminTabsUiSelfTest: all menus, wallet/history subtabs, one-window navigation, cached forms and filters, confirmation ownership, inactive polling, late responses and compact screenshots");
        } finally {
            fx(() -> { app.stop(); for (Window window : new ArrayList<>(Window.getWindows())) { window.hide(); } return null; });
            Platform.exit();
        }
    }

    private static void navigate(Stage stage, String id, int selectedIndex) throws Exception {
        fx(() -> {
            ((Button) stage.getScene().lookup("#" + id)).fire();
            TabPane tabs = (TabPane) stage.getScene().lookup("#server-admin-tabs");
            TestSupport.equals(selectedIndex, tabs.getSelectionModel().getSelectedIndex(), "sidebar selects matching tab: " + id);
            TestSupport.check(stage.getScene().lookup("#" + id).getStyleClass().contains("nav-active"), "active sidebar follows selected tab");
            TestSupport.equals(1L, Window.getWindows().stream().filter(Window::isShowing).count(), "navigation never opens another window");
            return null;
        });
    }

    private static DialogPane showingDialog() {
        return new ArrayList<>(Window.getWindows()).stream().filter(Window::isShowing)
                .map(window -> window.getScene().getRoot().lookup(".dialog-pane"))
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst().orElse(null);
    }

    private static void confirmYes() throws Exception {
        waitFor(() -> showingDialog() != null, "confirmation is visible");
        fx(() -> { ((Button) showingDialog().lookupButton(ButtonType.YES)).fire(); return null; });
    }

    private static void snapshot(Stage stage, String name) throws Exception {
        fx(() -> {
            Scene scene = stage.getScene(); scene.getRoot().applyCss(); scene.getRoot().layout();
            var image = scene.snapshot(null);
            BufferedImage bitmap = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int row = 0; row < bitmap.getHeight(); row++) {
                for (int column = 0; column < bitmap.getWidth(); column++) { bitmap.setRGB(column, row, image.getPixelReader().getArgb(column, row)); }
            }
            Path output = Path.of("target", "admin-tabs", name + ".png"); Files.createDirectories(output.getParent());
            ImageIO.write(bitmap, "png", output.toFile()); return null;
        });
    }

    private static Object field(Object target, String name) throws Exception {
        var member = target.getClass().getDeclaredField(name); member.setAccessible(true); return member.get(target);
    }

    private static <Result> Result fx(Callable<Result> action) throws Exception {
        var result = new CompletableFuture<Result>();
        Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } });
        return result.get(20, TimeUnit.SECONDS);
    }

    private static void waitFor(Callable<Boolean> condition, String description) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) { if (fx(condition)) { return; } Thread.sleep(50); }
        throw new AssertionError("Timeout: " + description);
    }
}
