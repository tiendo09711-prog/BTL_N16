package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.ptit.btl16.client.fx.*;
import vn.ptit.btl16.client.model.*;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.*;
import vn.ptit.btl16.common.fx.DesktopTheme;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Isolated data and a real media endpoint verify the four personal account screens together. */
public final class AccountScreensUiSelfTest {
    public static void main(String[] args) throws Exception {
        Path data = Files.createTempDirectory("btl16-account-screens-"); Path config = data.resolve("server.properties");
        Files.writeString(config, "server.tcp.enabled=false\nserver.websocket.bindAddress=127.0.0.1\nserver.websocket.port=0\nmedia.port=0\ndb.driver=org.h2.Driver\ndb.user=sa\ndb.name=accounts\nsecurity.passwordIterations=10000\n");
        System.setProperty("btl16.server.config", config.toString()); System.setProperty("btl16.dataDir", data.toString());
        Platform.startup(() -> Platform.setImplicitExit(false));
        FxClientController controller = null;
        try (var server = vn.ptit.btl16.server.ServerApplication.create(ServerConfig.loadDefault())) {
            server.start();
            try (var transport = new WebSocketClientTransport("ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws", 2097152, 5000, 10000)) {
                transport.connect().get(10, TimeUnit.SECONDS); var account = new AccountApi(transport); var auctions = new AuctionApi(transport);
                ok(account.register("account_preview", "Password123!", "Thiên", "thien@example.test", "0901234567")); var identity = ok(account.login("account_preview", "Password123!"));
                var model = new ClientAppModel(); model.applyIdentity(identity); long user = model.getUserId();
                String request = UUID.randomUUID().toString(); ok(new WalletApi(transport).submit(request, new java.math.BigDecimal("886858000"))); server.wallets().review(request, true);
                String description = "Bình gốm trang trí đã qua sử dụng, họa tiết rõ nét, bề mặt có một vết nhỏ ở phía sau, ảnh là sản phẩm thực tế.";
                var product = ok(TestMedia.create(auctions, "GOM34", "Bình gốm trang trí", description)); long firstProduct = Long.parseLong(product.get("productId"));
                long secondProduct = Long.parseLong(ok(TestMedia.create(auctions, "DONG23", "Tượng đồng trưng bày", description)).get("productId"));
                long inactive = Long.parseLong(ok(TestMedia.create(auctions, "GO32", "Đồ gỗ chạm khắc", description)).get("productId")); ok(auctions.deactivateProduct(inactive));
                long featuredRoom = Long.parseLong(ok(auctions.createAuction(firstProduct, new java.math.BigDecimal("100000"), new java.math.BigDecimal("10000"), 10)).get("auctionId"));
                controller = fx(() -> {
                    Stage stage = new Stage(); var view = new FxClientController(stage, ClientConfig.loadDefault(), model, transport, account, auctions);
                    invoke(view, "buildScenes"); stage.setScene((Scene) field(view, "mainScene")); DesktopTheme.fitOnScreen(stage, 1250, 940); stage.show(); return view;
                });
                FxClientController view = controller; Stage owner = (Stage) field(view, "stage");
                fx(() -> {
                    var root = owner.getScene().getRoot(); root.applyCss(); root.layout();
                    var avatar = (Button) root.lookup("#account-profile-navigation");
                    TestSupport.check(avatar != null && avatar.getParent().getParent().getStyleClass().contains("market-account"), "account profile opens from the existing footer avatar");
                    TestSupport.check(avatar.isFocusTraversable() && "Tài khoản".equals(avatar.getTooltip().getText()), "avatar exposes keyboard navigation and an account tooltip");
                    TestSupport.check(avatar.getWidth() == 31 && avatar.getHeight() == 31, "interactive avatar keeps its compact circular dimensions");
                    TestSupport.check(root.lookupAll(".sidebar-navigation .button").stream().noneMatch(node -> "Tài khoản".equals(((Button) node).getText())), "sidebar no longer contains a separate account menu item");
                    return null;
                });
                fire(owner.getScene().getRoot(), "account-wallet-navigation");
                waitFor(() -> owner.getScene().lookup("#account-wallet") != null && labels(owner.getScene().getRoot()).contains("886.858.000"), "embedded wallet loads actual balance");
                TestSupport.check(fx(() -> Window.getWindows().stream().filter(Window::isShowing).count()) == 1, "wallet stays in the main shell without a new window");
                screenshot(owner.getScene().getRoot(), "target/account-wallet.png");
                fx(() -> { ((TextField) owner.getScene().lookup("#wallet-amount")).setText("100000"); return null; });
                fire(owner.getScene().getRoot(), "wallet-generate");
                TestSupport.check(fx(() -> ((ImageView) owner.getScene().lookup("#wallet-qr-image")).getImage() != null), "embedded wallet generates the same real QR");
                exerciseWalletTransactions(owner);
                WalletPane oldWallet = (WalletPane) fx(() -> owner.getScene().lookup("#account-wallet"));
                fire(owner.getScene().getRoot(), "account-marketplace-navigation");
                TestSupport.check(fx(() -> owner.getScene().lookup("#account-wallet") == null), "marketplace navigation restores the auction workspace");
                TestSupport.check(fx(() -> ((javafx.animation.Timeline) field(oldWallet, "poll")).getStatus() == javafx.animation.Animation.Status.STOPPED), "leaving the wallet stops its refresh timer");
                waitFor(() -> owner.getScene().lookup("#featured-auction-" + featuredRoom) != null, "featured room loads through the real authenticated endpoint");
                fire(owner.getScene().getRoot(), "featured-auction-" + featuredRoom);
                waitFor(() -> Objects.equals(model.getJoinedAuctionId(), featuredRoom), "featured card enters its room through the controller and network");
                TestSupport.check(fx(() -> owner.getScene().lookup("#featured-auctions") == null || !owner.getScene().lookup("#featured-auctions").isVisible()), "featured area is hidden while inside the room");
                fx(() -> { ((Button) field(view, "leaveButton")).fire(); return null; });
                waitFor(() -> model.getJoinedAuctionId() == null, "leaving a featured room restores the lobby");
                fx(() -> { invoke(view, "showProducts"); return null; }); Stage productsWindow = waitWindow("#seller-products-page");
                TestSupport.check(productsWindow == owner && fx(() -> Window.getWindows().stream().filter(Window::isShowing).count()) == 1, "selling products stays inside the main shell");
                TestSupport.check(fx(() -> ((Button) owner.getScene().lookup("#account-products-navigation")).getText().equals("Sản phẩm bán")
                        && owner.getScene().lookup("#account-products-navigation").getStyleClass().contains("nav-active")), "selling page is renamed and selected in navigation");
                Node productsPage = fx(() -> owner.getScene().lookup("#seller-products-page"));
                TestSupport.check(fx(() -> productsPage.lookup(".seller-actions").lookupAll(".button").stream().map(Button.class::cast).map(Button::getText).toList())
                        .equals(List.of("Thêm sản phẩm", "Làm mới")), "redundant edit, media and delete footer buttons are removed");
                @SuppressWarnings("unchecked") TableView<ClientProduct> table = (TableView<ClientProduct>) fx(() -> productsWindow.getScene().lookup("#seller-products"));
                waitFor(() -> table.getItems().size() == 3, "products load from the server");
                waitFor(() -> productsWindow.getScene().getRoot().lookupAll(".image-view").stream().filter(ImageView.class::isInstance).map(ImageView.class::cast).anyMatch(image -> image.getImage() != null && !image.getImage().isError() && image.getImage().getProgress() == 1), "first real product thumbnail loads through media access");
                screenshot(productsWindow.getScene().getRoot(), "target/account-products.png");
                fx(() -> { ((TextField) productsWindow.getScene().lookup("#seller-product-search")).setText(product.get("code")); return null; });
                TestSupport.equals(1, fx(() -> table.getItems().size()), "product search filters by code");
                TestSupport.equals(firstProduct, fx(() -> table.getSelectionModel().getSelectedItem().getProductId()), "search keeps selected product details in sync");
                fx(() -> { ((ToggleButton) productsWindow.getScene().lookup("#seller-grid-view")).fire(); return null; });
                TestSupport.check(fx(() -> productsWindow.getScene().lookup("#seller-product-grid").isVisible()), "grid toggle shows product cards");
                TestSupport.check(fx(() -> productsPage.lookup("#seller-product-grid").lookupAll(".account-more").isEmpty()), "auctioning product cards hide all per-product actions");
                fx(() -> { ((ToggleButton) productsWindow.getScene().lookup("#seller-list-view")).fire(); ((TextField) productsWindow.getScene().lookup("#seller-product-search")).clear(); @SuppressWarnings("unchecked") var filter = (ComboBox<String>) productsWindow.getScene().lookup("#seller-product-filter"); filter.setValue("UNTRADED"); return null; });
                TestSupport.equals(2, fx(() -> table.getItems().size()), "untraded filter is independent of activation");
                fx(() -> { @SuppressWarnings("unchecked") var filter = (ComboBox<String>) productsWindow.getScene().lookup("#seller-product-filter"); filter.setValue("AUCTIONING"); return null; });
                TestSupport.equals(firstProduct, fx(() -> table.getItems().getFirst().getProductId()), "open auction appears under auctioning");
                // A response from an earlier server can still include acquired products; this selling page hides them.
                var statusWire = new LinkedHashMap<>(ok(auctions.myProducts()).getWireMessage().getData());
                for (int i = 0; i < 3; i++) { String p = "product." + i + "."; long id = Long.parseLong(statusWire.get(p + "productId")); if (id != firstProduct && id != inactive) { statusWire.put(p + "transactionStatus", "SOLD"); } }
                var statusProducts = ClientWireParser.products(statusWire);
                statusProducts.add(ClientWireParser.product(Map.of("productId", "991", "ownerId", "999", "code", "WON991",
                        "name", "Bình gốm đã sở hữu", "active", "true", "transactionStatus", "OWNED"), ""));
                fx(() -> {
                    var root = productsWindow.getScene().lookup("#seller-products-page"); var setter = root.getClass().getDeclaredMethod("setProducts", List.class, Long.class); setter.setAccessible(true); setter.invoke(root, statusProducts, null);
                    @SuppressWarnings("unchecked") var filter = (ComboBox<String>) productsWindow.getScene().lookup("#seller-product-filter"); filter.setValue("ALL");
                    TestSupport.equals(3, table.getItems().size(), "all selling statuses exclude acquired products");
                    TestSupport.check(!filter.getItems().contains("OWNED") && table.getItems().stream().noneMatch(p -> p.getProductId() == 991), "owned option and acquired product are absent");
                    var colors = Map.of("UNTRADED", "#171717", "AUCTIONING", "#12823b", "SOLD", "#cb2424");
                    for (var p : List.copyOf(table.getItems())) {
                        table.getSelectionModel().select(p); root.applyCss(); ((javafx.scene.Parent) root).layout();
                        var glyph = (javafx.scene.text.Text) table.lookup(".table-row-cell:selected .seller-status-" + p.getTransactionStatus().name().toLowerCase(Locale.ROOT) + " .text");
                        TestSupport.check(glyph != null, "selected product status is rendered");
                        TestSupport.equals(javafx.scene.paint.Color.web(colors.get(p.getTransactionStatus().name())), glyph.getFill(), "status keeps requested color while selected");
                    }
                    filter.setValue("SOLD"); TestSupport.equals(1, table.getItems().size(), "sold filter selects only the sold product");
                    filter.setValue("ALL"); return null;
                });
                screenshot(productsWindow.getScene().getRoot(), "target/product-transaction-states.png");
                fx(() -> { owner.setWidth(1177); owner.setHeight(733); return null; });
                screenshot(owner.getScene().getRoot(), "target/selling-products-compact.png");
                fire(owner.getScene().getRoot(), "account-wallet-navigation");
                TestSupport.check(fx(() -> owner.getScene().lookup("#seller-products-page") == null
                        && ((javafx.animation.Timeline) field(productsPage, "poll")).getStatus() == javafx.animation.Animation.Status.STOPPED
                        && table.getItems().isEmpty()), "leaving selling products stops polling and clears private rows");
                fire(owner.getScene().getRoot(), "account-products-navigation");
                waitFor(() -> ((TableView<?>) owner.getScene().lookup("#seller-products")).getItems().size() == 3, "selling products reloads when reopened");
                fire(owner.getScene().getRoot(), "seller-products-close");
                TestSupport.check(fx(() -> owner.isShowing() && owner.getScene().lookup("#seller-products-page") == null), "X restores the auction workspace in the same window");
                fx(() -> { DesktopTheme.fitOnScreen(owner, 1250, 940); return null; });
                auctionChoices(view, Set.of(secondProduct));
                ok(auctions.endAuction(featuredRoom));
                auctionChoices(view, Set.of(firstProduct, secondProduct));
                exerciseReceipts(owner, featuredRoom);
                fire(owner.getScene().getRoot(), "account-profile-navigation");
                Stage profile = waitWindow("#account-profile-form");
                TestSupport.check(fx(() -> !((TextField) profile.getScene().lookup("#profile-created-at")).getText().contains("T")), "created date is localized");
                TestSupport.check(fx(() -> ((TextField) profile.getScene().lookup("#profile-username")).isEditable()) == false, "username remains read only");
                TestSupport.check(fx(() -> {
                    var code = (TextField) profile.getScene().lookup("#profile-friend-code");
                    return !code.isEditable() && code.getText().matches("#[0-9]{4}") && code.getText().equals("#" + identity.get("friendCode"));
                }), "profile shows the account's assigned four-digit code as read only");
                screenshot(profile.getScene().getRoot(), "target/account-profile.png");
                fx(() -> {
                    ((TextField) profile.getScene().lookup("#profile-display-name")).setText("Thiên Nguyễn");
                    DialogPane dialog = (DialogPane) profile.getScene().getRoot();
                    var save = dialog.getButtonTypes().stream().filter(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow(); ((Button) dialog.lookupButton(save)).fire(); return null;
                });
                waitFor(() -> model.getDisplayName().equals("Thiên Nguyễn"), "profile save persists through account API");
                TestSupport.equals("T", fx(() -> ((Button) owner.getScene().lookup("#account-profile-navigation")).getText()), "avatar initial still updates after a profile edit");
                TestSupport.equals("Thiên Nguyễn", ok(account.getProfile()).get("displayName"), "profile update is stored by the server");
                fire(owner.getScene().getRoot(), "auction-rules-navigation"); Stage rules = waitWindow("#auction-rules-content");
                TestSupport.check(rules == owner && fx(() -> Window.getWindows().stream().filter(Window::isShowing).count()) == 1, "rules uses the existing account shell");
                TestSupport.check(fx(() -> owner.getScene().lookup("#auction-rules-navigation").getStyleClass().contains("nav-active")), "rules navigation is selected");
                TestSupport.equals(7, fx(() -> rules.getScene().getRoot().lookupAll(".rules-section").size()), "all seven existing rule sections retained");
                TestSupport.equals(3, fx(() -> rules.getScene().getRoot().lookupAll(".rules-checklist").size()), "principles, prohibited behavior and important notes are shown beside the process");
                TestSupport.check(fx(() -> labels(rules.getScene().getRoot())).contains("1.000.000.000"), "seller platform-fee threshold is documented");
                screenshot(rules.getScene().getRoot(), "target/account-rules.png");
                fx(() -> { ((TitledPane) owner.getScene().lookup("#auction-rules-fee-details")).setExpanded(true); ((ScrollPane) owner.getScene().lookup("#auction-rules-scroll")).setVvalue(1); return null; });
                TestSupport.check(fx(() -> labels(owner.getScene().getRoot())).contains("thứ hạng 1 tháng trước khi chốt phiên hiện tại"), "detailed fee and ranking rules remain available");
                screenshot(owner.getScene().getRoot(), "target/account-rules-bottom.png");
                fire(owner.getScene().getRoot(), "account-marketplace-navigation");
                TestSupport.check(fx(() -> owner.isShowing() && owner.getScene().lookup("#auction-rules-page") == null), "marketplace navigation restores the auction workspace after rules");
                System.out.println("[PASS] AccountScreensUiSelfTest: embedded wallet/transactions/receipts navigation and private-data lifecycle, deposit/withdrawal tabs, ledger filters, QR, product thumbnails/search/status/grid, auction eligibility, profile and rules with screenshots");
            }
        } finally {
            FxClientController current = controller;
            fx(() -> { if (current != null) { current.close(); } List.copyOf(Window.getWindows()).forEach(Window::hide); return null; }); Platform.exit();
        }
    }
    private static void exerciseReceipts(Stage owner, long room) throws Exception {
        fire(owner.getScene().getRoot(), "my-auction-receipts");
        waitFor(() -> owner.getScene().lookup("#account-receipts") != null, "receipts opens inside the main account shell");
        TestSupport.check(fx(() -> Window.getWindows().stream().filter(Window::isShowing).count()) == 1, "receipts does not create a second window");
        fx(() -> { @SuppressWarnings("unchecked") var scope = (ComboBox<String>) owner.getScene().lookup("#receipt-scope"); scope.setValue("HOSTED"); return null; });
        waitFor(() -> ((TableView<?>) owner.getScene().lookup("#receipts-history")).getItems().stream().anyMatch(value -> value instanceof Map<?, ?> entry && Long.toString(room).equals(entry.get("auctionId"))), "hosted receipts load through the authenticated server");
        screenshot(owner.getScene().getRoot(), "target/account-receipts.png");
        fire(owner.getScene().getRoot(), "receipt-view-" + room);
        waitFor(() -> owner.getScene().lookup("#receipt-personal-result") != null, "view button loads server-confirmed details in the embedded drawer");
        screenshot(owner.getScene().getRoot(), "target/account-receipt-detail.png");
        fire(owner.getScene().getRoot(), "receipt-close-detail");
        TestSupport.check(fx(() -> owner.getScene().lookup("#receipt-more-" + room) == null), "redundant more button is removed");
        fire(owner.getScene().getRoot(), "receipt-view-" + room);
        waitFor(() -> owner.getScene().lookup("#receipt-personal-result") != null, "view button reopens server-confirmed details");
        AuctionReceiptsPane oldReceipts = (AuctionReceiptsPane) fx(() -> owner.getScene().lookup("#account-receipts"));
        fx(() -> { oldReceipts.loadReceipt(room); ((Button) owner.getScene().lookup("#account-wallet-navigation")).fire(); return null; });
        waitFor(() -> owner.getScene().lookup("#account-wallet") != null && labels(owner.getScene().getRoot()).contains("886.858.000"), "wallet navigation replaces the embedded receipt");
        TestSupport.check(fx(() -> ((javafx.animation.Timeline) field(oldReceipts, "sessionGuard")).getStatus() == javafx.animation.Animation.Status.STOPPED
                && ((TableView<?>) field(oldReceipts, "history")).getItems().isEmpty()
                && oldReceipts.lookup("#receipt-personal-result") == null), "leaving receipts stops its guard and late private replies cannot repopulate it");
        fire(owner.getScene().getRoot(), "my-auction-receipts");
        waitFor(() -> owner.getScene().lookup("#account-receipts") != null, "receipts can reopen after another account page");
        fire(owner.getScene().getRoot(), "account-marketplace-navigation");
        TestSupport.check(fx(() -> owner.getScene().lookup("#account-receipts") == null), "marketplace restores the auction workspace after receipts");
    }
    private static void exerciseWalletTransactions(Stage owner) throws Exception {
        WalletPane firstWallet = (WalletPane) fx(() -> owner.getScene().lookup("#account-wallet"));
        TestSupport.check(fx(() -> owner.getScene().lookup("#wallet-ledger-card") == null), "ledger stays on its own page");
        TestSupport.equals(List.of("Nạp tiền", "Rút tiền"), fx(() -> ((TabPane) owner.getScene().lookup("#wallet-money-actions")).getTabs().stream().map(Tab::getText).toList()), "wallet exposes only deposit and simulated withdrawal");
        fire(owner.getScene().getRoot(), "account-transactions-navigation");
        waitFor(() -> owner.getScene().lookup("#transactions-entries") instanceof TableView<?> entries
                && entries.getItems().size() == 1, "transactions page displays the funded wallet history");
        TestSupport.check(fx(() -> ((javafx.animation.Timeline) field(firstWallet, "poll")).getStatus() == javafx.animation.Animation.Status.STOPPED), "leaving wallet stops polling");
        TestSupport.check(fx(() -> Window.getWindows().stream().filter(Window::isShowing).count()) == 1, "transactions stays in the main window");
        TestSupport.equals(List.of("", "TOPUP", "WITHDRAWAL", "PURCHASE", "SALE"), fx(() -> List.copyOf(((ComboBox<?>) owner.getScene().lookup("#transactions-type")).getItems())), "transaction filters match the supported money lifecycle");
        TestSupport.equals(5, fx(() -> ((TableView<?>) owner.getScene().lookup("#transactions-entries")).getColumns().size()), "history contains only the current wallet columns");
        selectTransactionsType(owner, "TOPUP", 1);
        screenshot(owner.getScene().getRoot(), "target/account-transactions-topup.png");
        selectTransactionsType(owner, "", 1);
        for (String type : List.of("WITHDRAWAL", "PURCHASE", "SALE")) { selectTransactionsType(owner, type, 0); }
        selectTransactionsType(owner, "", 1);
        screenshot(owner.getScene().getRoot(), "target/account-transactions.png");
        TransactionsPane oldTransactions = (TransactionsPane) fx(() -> owner.getScene().lookup("#account-transactions"));
        fire(owner.getScene().getRoot(), "account-wallet-navigation");
        waitFor(() -> owner.getScene().lookup("#wallet-money-actions") != null, "wallet opens again");
        TestSupport.check(fx(() -> ((javafx.animation.Timeline) field(oldTransactions, "poll")).getStatus() == javafx.animation.Animation.Status.STOPPED), "leaving transactions stops polling");
        waitFor(() -> ((Label) field(owner.getScene().lookup("#account-wallet"), "balance")).getText().contains("886.858.000"), "wallet balance is unchanged by account navigation");
    }
    private static void selectTransactionsType(Stage owner, String type, int expectedCount) throws Exception {
        fx(() -> { @SuppressWarnings("unchecked") var filter = (ComboBox<String>) owner.getScene().lookup("#transactions-type"); filter.setValue(type); return null; });
        waitFor(() -> {
            var pane = owner.getScene().lookup("#account-transactions"); var entries = (TableView<?>) owner.getScene().lookup("#transactions-entries");
            return !Boolean.TRUE.equals(field(pane, "loading")) && entries.getItems().size() == expectedCount
                    && (type.isEmpty() || entries.getItems().stream().allMatch(item -> item instanceof vn.ptit.btl16.common.wallet.WalletEntry entry && entry.type().equals(type)));
        }, "choosing " + type + " immediately filters server history without clicking the filter button");
    }
    private static void auctionChoices(FxClientController view, Set<Long> expected) throws Exception {
        fx(() -> { invoke(view, "showCreateAuction"); return null; });
        Stage window = waitWindow("#auction-create-product");
        fx(() -> {
            @SuppressWarnings("unchecked") var choices = (ComboBox<ClientProduct>) window.getScene().lookup("#auction-create-product");
            TestSupport.equals(expected, choices.getItems().stream().map(ClientProduct::getProductId).collect(java.util.stream.Collectors.toSet()), "auction picker only offers eligible products from the server");
            DialogPane dialog = (DialogPane) window.getScene().getRoot();
            var cancel = dialog.getButtonTypes().stream().filter(button -> button.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE).findFirst().orElseThrow();
            ((Button) dialog.lookupButton(cancel)).fire(); return null;
        });
        waitFor(() -> !window.isShowing(), "auction dialog closes after cancelling");
    }
    private static void fire(Node root, String id) throws Exception { fx(() -> { root.applyCss(); ((Button) root.lookup("#" + id)).fire(); return null; }); }
    private static Stage waitWindow(String id) throws Exception {
        waitFor(() -> Window.getWindows().stream().anyMatch(window -> window.isShowing() && window.getScene() != null && window.getScene().lookup(id) != null), "window " + id);
        return fx(() -> (Stage) Window.getWindows().stream().filter(window -> window.isShowing() && window.getScene() != null && window.getScene().lookup(id) != null).findFirst().orElseThrow());
    }
    private static String labels(Node root) { return root.lookupAll(".label").stream().map(node -> ((Label) node).getText()).collect(java.util.stream.Collectors.joining(" ")); }
    private static Object field(Object target, String name) throws Exception { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static void invoke(Object target, String method) throws Exception { var action = target.getClass().getDeclaredMethod(method); action.setAccessible(true); action.invoke(target); }
    private static ApiResponse ok(CompletableFuture<ApiResponse> request) throws Exception { var response = request.get(15, TimeUnit.SECONDS); TestSupport.check(response.isSuccess(), response.getMessageText()); return response; }
    private static <T> T fx(Callable<T> action) throws Exception { var future = new CompletableFuture<T>(); Platform.runLater(() -> { try { future.complete(action.call()); } catch (Throwable error) { future.completeExceptionally(error); } }); return future.get(15, TimeUnit.SECONDS); }
    private static void waitFor(Callable<Boolean> action, String message) throws Exception { long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15); while (System.nanoTime() < end) { if (fx(action)) { return; } Thread.sleep(40); } throw new AssertionError("Timeout: " + message); }
    private static void screenshot(Node root, String path) throws Exception { fx(() -> { root.applyCss(); if (root instanceof javafx.scene.Parent parent) { parent.layout(); } var image = root.snapshot(null, null); var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB); for (int y = 0; y < output.getHeight(); y++) { for (int x = 0; x < output.getWidth(); x++) { output.setRGB(x, y, image.getPixelReader().getArgb(x, y)); } } javax.imageio.ImageIO.write(output, "png", new java.io.File(path)); return null; }); }
}
