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
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.common.util.RevenuePeriod;
import vn.ptit.btl16.common.wallet.WalletAccount;
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
                AuctionApi auctions = new AuctionApi(client);
                for (int index = 0; index < 6; index++) {
                    var product = TestMedia.create(auctions,"ADM" + index,"Studio camera " + index,TestMedia.DESCRIPTION).get(10,TimeUnit.SECONDS);
                    TestSupport.check(product.isSuccess(),"create overview fixture product");
                    var room = auctions.createAuction(Long.parseLong(product.get("productId")),new BigDecimal("1000000"),new BigDecimal("10000"),60).get(10,TimeUnit.SECONDS);
                    TestSupport.check(room.isSuccess(),"create overview fixture room");
                }
            }
            String funding = UUID.randomUUID().toString();
            server.wallets().submit(userId, funding, new BigDecimal("30000"));
            server.wallets().review(funding, true);
            server.wallets().submit(userId, UUID.randomUUID().toString(), new BigDecimal("1000"));
            server.wallets().submitWithdrawal(userId, UUID.randomUUID().toString(), new BigDecimal("2000"));

            TabPane mainTabs = fx(() -> (TabPane) stage.getScene().lookup("#server-admin-tabs"));
            fx(() -> {
                TestSupport.equals(4, mainTabs.getTabs().size(), "all four admin workspaces exist");
                TestSupport.check(!mainTabs.lookup(".tab-header-area").isVisible(), "main navigation is not duplicated above the page");
                for (String id : new String[]{"server-overview-open", "server-wallet-open", "server-cash-flow-open", "server-auction-sessions-open"}) {
                    Button button = (Button) stage.getScene().lookup("#" + id);
                    TestSupport.check(button.getGraphic()!=null, "navigation uses drawn icons: " + id);
                    TestSupport.check(button.getFont().getSize()>=13, "navigation remains readable: " + id);
                }
                TestSupport.equals(TabPane.TabClosingPolicy.UNAVAILABLE, mainTabs.getTabClosingPolicy(), "admin sections cannot be accidentally closed");
                return null;
            });
            waitFor(() -> ((TableView<?>)stage.getScene().lookup("#server-overview-auctions")).getItems().size()==4
                    && ((Label)field(app,"overviewRoomCount")).getText().contains("/ 6 phiên"),"overview shows only four of six recent sessions");
            snapshot(stage, "overview");
            long clickedRoom = fx(() -> {
                TableView<?> recent = (TableView<?>)stage.getScene().lookup("#server-overview-auctions");
                TestSupport.check(recent.getHeight()<=240,"overview list remains compact instead of repeating the full marketplace");
                var row = recent.lookupAll(".table-row-cell").stream().filter(node -> node instanceof javafx.scene.control.TableRow<?> item && !item.isEmpty()).findFirst().orElseThrow();
                long roomId = ((vn.ptit.btl16.server.auction.model.AuctionSnapshot)((javafx.scene.control.TableRow<?>)row).getItem()).getAuctionId();
                row.fireEvent(new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_CLICKED,1,1,1,1,
                        javafx.scene.input.MouseButton.PRIMARY,1,false,false,false,false,false,false,false,false,false,false,null));
                TestSupport.equals(3,mainTabs.getSelectionModel().getSelectedIndex(),"one click on an overview room opens the sessions workspace");
                return roomId;
            });
            Object sessions = fx(() -> mainTabs.getSelectionModel().getSelectedItem().getContent());
            waitFor(() -> !(boolean)field(sessions,"loading"),"overview room details load");
            fx(() -> {
                TestSupport.equals(3,((TabPane)stage.getScene().lookup("#server-session-tabs")).getTabs().size(),"overview routes to the full existing three-tab room detail");
                TestSupport.equals(clickedRoom,field(sessions,"selectedRoom"),"overview opens exactly the clicked room, not a different recent session");
                TestSupport.equals(1L,Window.getWindows().stream().filter(Window::isShowing).count(),"overview navigation stays in the same server window");
                return null;
            });
            snapshot(stage,"overview-room-detail");
            fx(() -> { ((Button)stage.getScene().lookup("#server-session-back")).fire(); return null; });
            navigate(stage,"server-overview-open",0);
            fx(() -> { ((Button)stage.getScene().lookup("#server-overview-all-sessions")).fire(); return null; });
            waitFor(() -> !(boolean)field(sessions,"loading"),"view all sessions loads");
            fx(() -> { TestSupport.check(stage.getScene().lookup("#server-session-cards")!=null,"view all returns to the complete sessions browser"); return null; });
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
                TestSupport.check(historyHeight >= 254, "account history reserves at least four full rows instead of being squeezed by a divider: " + historyHeight);
                checkNumericHeaders((TableView<?>)field(wallet,"accounts"));
                TestSupport.check(stage.getScene().lookup("#server-wallet-nav")!=null && stage.getScene().lookup("#server-wallet-history-nav")!=null,"wallet sections and histories use matching rounded navigation");
                return null;
            });
            checkAccountSizing(stage,wallet,"wide");
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
            fx(() -> {
                var cards = (javafx.scene.layout.TilePane) stage.getScene().lookup("#cash-flow-cards");
                TestSupport.equals(4, cards.getPrefColumns(), "wide cash-flow page shows four summary cards");
                TestSupport.check(!stage.getScene().lookup("#cash-flow-review").isManaged(), "zero review count does not consume space");
                TestSupport.check(((Label) stage.getScene().lookup("#cash-flow-balance")).getFont().getSize()>=22, "financial totals remain readable");
                checkNumericHeaders((TableView<?>)stage.getScene().lookup("#cash-flow-table"));
                var first = cards.getChildren().getFirst();
                TestSupport.equals(first.getLayoutY(),cards.getChildren().get(3).getLayoutY(),"four metric cards really fit on the same wide row");
                return null;
            });
            navigate(stage, "server-auction-sessions-open", 3);
            Object logs = fx(() -> mainTabs.getSelectionModel().getSelectedItem().getContent());
            waitFor(() -> !(boolean) field(logs, "loading"), "operation log loads");
            fx(() -> {
                ((ComboBox<?>) field(logs, "type")).getSelectionModel().select(1);
                ((Button) stage.getScene().lookup("#server-session-filter")).fire();
                TestSupport.equals(Animation.Status.STOPPED, ((Timeline) field(cash, "poll")).getStatus(), "hidden cash flow stops polling");
                TestSupport.equals(null, stage.getScene().lookup("#server-operations-open"), "operation log is absent from navigation");
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
                navigate(stage, "server-auction-sessions-open", 3);
                fx(() -> { TestSupport.equals(1, ((ComboBox<?>) field(logs, "type")).getSelectionModel().getSelectedIndex(), "session filter survives navigation"); return null; });
            }

            ExecutorService worker = fx(() -> (ExecutorService) field(app, "worker"));
            CountDownLatch release = new CountDownLatch(1);
            worker.execute(() -> { try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); } });
            try {
                navigate(stage, "server-wallet-open", 1);
                navigate(stage, "server-auction-sessions-open", 3);
            } finally { release.countDown(); }
            navigate(stage, "server-wallet-open", 1);
            waitFor(() -> !(boolean) field(wallet, "loading"), "wallet resumes after a response arrives while hidden");

            fx(() -> { stage.setWidth(980); stage.setHeight(680); return null; });
            waitFor(() -> stage.getScene().getWidth()<1000,"compact window resize has reached the scene before screenshots");
            fx(() -> { mainTabs.getSelectionModel().select(1); walletTabs.getSelectionModel().selectFirst(); return null; });
            snapshot(stage, "compact-accounts");
            checkAccountSizing(stage,wallet,"compact");
            fx(() -> { walletTabs.getSelectionModel().select(1); return null; });
            snapshot(stage,"compact-topups");
            fx(() -> { walletTabs.getSelectionModel().select(2); return null; });
            snapshot(stage,"compact-withdrawals");
            fx(() -> {
                Node action = (Node)field(wallet,"approveWithdrawal");
                TestSupport.check(action.localToScene(action.getLayoutBounds()).getMaxY()<=stage.getScene().getHeight(),"review action stays visible in compact windows");
                walletTabs.getSelectionModel().selectFirst(); return null;
            });
            for (int index = 0; index < 4; index++) {
                final int selected = index;
                fx(() -> { mainTabs.getSelectionModel().select(selected); return null; });
                snapshot(stage, "compact-" + index);
                if (index==0) {
                    fx(() -> {
                        var scroll = (javafx.scene.control.ScrollPane)mainTabs.getTabs().getFirst().getContent();
                        Node card = stage.getScene().lookup("#server-overview-session-card");
                        double top = scroll.getContent().sceneToLocal(card.localToScene(card.getLayoutBounds())).getMinY();
                        double distance = scroll.getContent().getLayoutBounds().getHeight()-scroll.getViewportBounds().getHeight();
                        scroll.setVvalue(Math.min(1,top/distance)); return null;
                    });
                    snapshot(stage,"compact-overview-sessions");
                    fx(() -> { ((javafx.scene.control.ScrollPane)mainTabs.getTabs().getFirst().getContent()).setVvalue(0); return null; });
                }
                fx(() -> {
                    Node content = mainTabs.getSelectionModel().getSelectedItem().getContent();
                    TestSupport.check(content.getBoundsInParent().getWidth() > 600, "compact page remains usable");
                    if (selected==2) {
                        var cards = (javafx.scene.layout.TilePane) stage.getScene().lookup("#cash-flow-cards");
                        TestSupport.equals(2, cards.getPrefColumns(), "compact cash flow wraps cards into two columns");
                        TestSupport.check(stage.getScene().lookup("#cash-flow-table").getBoundsInParent().getHeight()>140, "compact cash flow leaves room for transactions");
                    }
                    String[] buttons = {"server-overview-open", "server-wallet-open", "server-cash-flow-open", "server-auction-sessions-open"};
                    TestSupport.check(stage.getScene().lookup("#" + buttons[selected]).getStyleClass().contains("nav-active"), "selecting a tab updates the matching sidebar item");
                    TestSupport.equals(1L, Window.getWindows().stream().filter(Window::isShowing).count(), "compact navigation stays in one window");
                    return null;
                });
            }
            System.out.println("[PASS] ServerAdminTabsUiSelfTest: compact overview list/detail routing, aligned financial columns, rounded wallet navigation, all menus, cached forms/filters, confirmations, polling and compact screenshots");
        } finally {
            fx(() -> { app.stop(); for (Window window : new ArrayList<>(Window.getWindows())) { window.hide(); } return null; });
            Platform.exit();
        }
    }

    private static void checkAccountSizing(Stage stage, Object wallet, String size) throws Exception {
        Object selected = fx(() -> ((TableView<?>)field(wallet,"accounts")).getSelectionModel().getSelectedItem());
        fx(() -> {
            var scroll = (javafx.scene.control.ScrollPane)stage.getScene().lookup("#server-wallet-account-scroll");
            TestSupport.check(scroll.getContent().getLayoutBounds().getHeight()>scroll.getViewportBounds().getHeight(),"two generous account cards use page scrolling rather than compressing their tables");
            TestSupport.check(((TableView<?>)field(wallet,"accounts")).getHeight()>=254,"account list reserves multiple complete rows");
            scroll.setVvalue(1); return null;
        });
        snapshot(stage,"accounts-history-scrolled-" + size);
        fx(() -> { ((Button)stage.getScene().lookup("#server-wallet-expand-history")).fire(); return null; });
        snapshot(stage,"accounts-history-expanded-" + size);
        fx(() -> {
            TestSupport.check(!stage.getScene().lookup("#server-wallet-accounts-card").isManaged(),"expanding history temporarily hides the account card");
            var history = (TabPane)stage.getScene().lookup("#server-wallet-history-tabs");
            var table = (TableView<?>)((javafx.scene.layout.StackPane)history.getSelectionModel().getSelectedItem().getContent()).getChildren().getFirst();
            var scroll = (javafx.scene.control.ScrollPane)stage.getScene().lookup("#server-wallet-account-scroll");
            TestSupport.check(table.getHeight()>=scroll.getViewportBounds().getHeight()-200,"expanded history uses the available page height");
            TestSupport.check(scroll.getContent().getLayoutBounds().getHeight()<=scroll.getViewportBounds().getHeight()+1,"focused history fits the viewport instead of introducing another vertical scroll");
            TestSupport.equals("Thu gọn",((Button)stage.getScene().lookup("#server-wallet-expand-history")).getText(),"focused card provides an obvious restore action");
            ((Button)stage.getScene().lookup("#server-wallet-expand-history")).fire();
            ((Button)stage.getScene().lookup("#server-wallet-expand-accounts")).fire(); return null;
        });
        snapshot(stage,"accounts-list-expanded-" + size);
        fx(() -> {
            TestSupport.check(!stage.getScene().lookup("#server-wallet-history-card").isManaged(),"expanding the account list temporarily hides history");
            var scroll = (javafx.scene.control.ScrollPane)stage.getScene().lookup("#server-wallet-account-scroll");
            TestSupport.check(((TableView<?>)field(wallet,"accounts")).getHeight()>=scroll.getViewportBounds().getHeight()-100,"expanded account list fills the available page");
            TestSupport.equals(selected,((TableView<?>)field(wallet,"accounts")).getSelectionModel().getSelectedItem(),"expanding and restoring never loses the selected account");
            ((Button)stage.getScene().lookup("#server-wallet-expand-accounts")).fire(); return null;
        });
        snapshot(stage,"accounts-restored-" + size);
        fx(() -> {
            TestSupport.check(stage.getScene().lookup("#server-wallet-history-card").isManaged()
                    && stage.getScene().lookup("#server-wallet-accounts-card").isManaged(),"restoring brings both cards back without recreating the page");
            return null;
        });
        checkAccountOverflow(stage,wallet,size);
    }

    @SuppressWarnings("unchecked")
    private static void checkAccountOverflow(Stage stage, Object wallet, String size) throws Exception {
        TableView<WalletAccount> table = fx(() -> (TableView<WalletAccount>)field(wallet,"accounts"));
        var original = fx(() -> new ArrayList<>(table.getItems()));
        Timeline poll = fx(() -> (Timeline)field(wallet,"poll"));
        boolean polling = fx(() -> poll.getStatus()==Animation.Status.RUNNING);
        try {
            fx(() -> { poll.stop(); return null; });
            waitFor(() -> !(boolean)field(wallet,"loading"),"pending wallet refresh finishes before overflow fixture");
            fx(() -> {
                WalletAccount account = table.getSelectionModel().getSelectedItem();
                for (int index=0; index<20; index++) {
                    table.getItems().add(new WalletAccount(1000+index,"scroll_"+index,"Scroll account "+index,
                            account.email(),account.phone(),account.createdAt(),account.lastLoginAt(),account.balance(),account.reserved()));
                }
                return null;
            });
            snapshot(stage,"accounts-many-rows-" + size);
            fx(() -> {
                TestSupport.equals(350.0,table.getPrefHeight(),"normal table grows to six rows without making the page unbounded");
                TestSupport.check(table.lookupAll(".scroll-bar").stream().anyMatch(node -> node instanceof javafx.scene.control.ScrollBar bar
                        && bar.getOrientation()==javafx.geometry.Orientation.VERTICAL && bar.isVisible()),"large account lists retain a usable vertical table scrollbar");
                table.scrollTo(table.getItems().size()-1); return null;
            });
            snapshot(stage,"accounts-last-row-" + size);
            fx(() -> {
                TestSupport.check(table.lookupAll(".table-row-cell").stream().anyMatch(node -> node instanceof javafx.scene.control.TableRow<?> row
                        && row.isVisible() && row.getIndex()==table.getItems().size()-1),"scrolling reaches the final account row");
                return null;
            });
        } finally {
            fx(() -> {
                table.getItems().remove(original.size(),table.getItems().size()); table.scrollTo(0);
                if (polling) { poll.play(); }
                return null;
            });
        }
    }

    private static void checkNumericHeaders(TableView<?> table) {
        var labels = table.lookupAll(".column-header.admin-number-column .label");
        TestSupport.check(!labels.isEmpty(),"financial table has numeric column headers");
        for (Node node : labels) { TestSupport.equals(javafx.geometry.Pos.CENTER_RIGHT,((Label)node).getAlignment(),"financial headers align with right-aligned money values"); }
        for (Node node : table.lookupAll(".table-cell.admin-number-column")) {
            TestSupport.equals(javafx.geometry.Pos.CENTER_RIGHT,((javafx.scene.control.TableCell<?,?>)node).getAlignment(),"financial values use the same alignment as their headers");
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
