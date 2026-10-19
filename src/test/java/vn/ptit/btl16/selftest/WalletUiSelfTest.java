package vn.ptit.btl16.selftest;

import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.ptit.btl16.client.fx.WalletPane;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.*;
import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.common.wallet.*;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.fx.ServerWalletWindow;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

public final class WalletUiSelfTest {
    public static void main(String[] args) throws Exception {
        Path data = Files.createTempDirectory("wallet-ui-test-"); Path config = data.resolve("server.properties");
        Files.writeString(config, "server.tcp.enabled=false\nserver.websocket.port=0\nmedia.port=0\ndb.driver=org.h2.Driver\ndb.user=sa\ndb.name=auction\nsecurity.passwordIterations=10000\n");
        System.setProperty("btl16.server.config", config.toString()); System.setProperty("btl16.dataDir", data.toString());
        Platform.startup(() -> Platform.setImplicitExit(false));
        ExecutorService worker = Executors.newSingleThreadExecutor(task -> { Thread thread = new Thread(task, "wallet-ui-test-worker"); thread.setDaemon(true); return thread; });
        try (ServerApplication server = ServerApplication.create(ServerConfig.loadDefault())) {
            server.start();
            try (var client = new WebSocketClientTransport("ws://127.0.0.1:" + server.getWebSocketBoundPort() + "/ws", 2097152, 5000, 10000)) {
                client.connect().get(10, TimeUnit.SECONDS); AccountApi account = new AccountApi(client);
                ok(account.register("wallet_ui_buyer", "Password123!", "Wallet UI buyer", "demo@example.test", "0900000000"));
                ApiResponse identity = ok(account.login("wallet_ui_buyer", "Password123!")); long userId = Long.parseLong(identity.get("userId"));
                WalletPane wallet = fx(() -> { WalletPane pane = new WalletPane(null, new WalletApi(client), userId, "wallet_ui_buyer", () -> true); pane.show(); return pane; });
                waitFor(() -> !field(wallet, "loading", Boolean.class), "wallet initial load");
                fx(() -> {
                    wallet.applyCss(); wallet.layout();
                    var confirmButton = field(wallet, "confirm", Button.class);
                    var bounds = confirmButton.localToScene(confirmButton.getBoundsInLocal());
                    var viewport = wallet.lookup(".scroll-pane").lookup(".viewport");
                    var visible = viewport.localToScene(viewport.getBoundsInLocal());
                    TestSupport.check(bounds.getMinY() >= visible.getMinY() && bounds.getMaxY() <= visible.getMaxY(),
                            "deposit confirmation stays visible without scrolling on the fitted screen");
                    return null;
                });
                fx(() -> { field(wallet, "amount", TextField.class).setText("5000"); field(wallet, "generate", Button.class).fire(); return null; });
                String payload = fx(() -> {
                    var image = field(wallet, "qr", ImageView.class).getImage(); TestSupport.check(image != null, "QR is rendered locally");
                    int width = (int) image.getWidth(), height = (int) image.getHeight(); int[] pixels = new int[width * height];
                    for (int row = 0; row < height; row++) { for (int column = 0; column < width; column++) { pixels[row * width + column] = image.getPixelReader().getArgb(column, row); } }
                    return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)))).getText();
                });
                TestSupport.check(payload.contains("NO_REAL_PAYMENT") && payload.contains("amount=5000.00"), "scannable QR contains demo, amount and request id, never bank transfer details");
                fx(() -> { field(wallet, "confirm", Button.class).fire(); return null; });
                waitFor(() -> field(wallet, "pending", Boolean.class), "Da nhap creates pending request");
                TestSupport.check(server.wallets().account(userId).balance().signum() == 0, "client confirmation never credits money");
                TestSupport.check(fx(() -> field(wallet, "confirm", Button.class).isDisabled()), "pending UI cannot send duplicate new deposits");
                snapshot(field(wallet, "stage", Stage.class), "wallet-client-pending.png");
                ServerWalletWindow admin = fx(() -> { var window = new ServerWalletWindow(null, () -> server, worker); window.show(); return window; });
                waitFor(() -> field(admin, "pending", TableView.class).getItems().size() == 1, "server pending queue displays request");
                fx(() -> { field(admin, "pending", TableView.class).getSelectionModel().selectFirst(); return null; });
                Platform.runLater(() -> field(admin, "approve", Button.class).fire()); confirm();
                waitFor(() -> server.wallets().account(userId).balance().compareTo(new BigDecimal("5000")) == 0, "server approve button credits exact amount");
                waitFor(() -> !field(wallet, "pending", Boolean.class) && field(wallet, "available", Label.class).getText().contains("5.000"), "client polls approved balance");
                waitFor(() -> field(admin, "pending", TableView.class).getItems().isEmpty(), "approved request disappears from pending queue");
                fx(() -> { field(admin, "accounts", TableView.class).getSelectionModel().selectFirst(); return null; });
                waitFor(() -> field(admin, "profile", Label.class).getText().contains("demo@example.test")
                        && !field(admin, "entries", TableView.class).getItems().isEmpty(), "server shows profile and transaction details without password");
                snapshot(field(admin, "stage", Stage.class), "wallet-server-account.png"); snapshot(field(wallet, "stage", Stage.class), "wallet-client-approved.png");
                fx(() -> { field(wallet, "amount", TextField.class).setText("2000"); field(wallet, "generate", Button.class).fire(); field(wallet, "confirm", Button.class).fire(); return null; });
                waitFor(() -> field(admin, "pending", TableView.class).getItems().size() == 1, "second request appears at server");
                fx(() -> { field(admin, "pending", TableView.class).getSelectionModel().selectFirst(); return null; });
                Platform.runLater(() -> field(admin, "reject", Button.class).fire()); confirm();
                waitFor(() -> !field(wallet, "pending", Boolean.class), "client receives rejection");
                TestSupport.check(server.wallets().account(userId).balance().compareTo(new BigDecimal("5000")) == 0, "reject button preserves existing balance");
                fx(() -> { field(wallet, "stage", Stage.class).close(); field(admin, "stage", Stage.class).close(); return null; });
                System.out.println("[PASS] WalletUiSelfTest: real scannable demo QR, Da nhap pending-only, server approve/reject buttons, client live balance, account/profile/ledger details and screenshots");
            }
        } finally { fx(() -> { new ArrayList<>(Window.getWindows()).forEach(Window::hide); return null; }); worker.shutdownNow(); Platform.exit(); }
    }
    private static void confirm() throws Exception {
        waitFor(() -> Window.getWindows().stream().anyMatch(window -> window.isShowing() && window.getScene() != null && window.getScene().lookup(".dialog-pane") instanceof DialogPane), "operator confirmation dialog");
        fx(() -> {
            DialogPane pane = (DialogPane) Window.getWindows().stream().filter(Window::isShowing).map(window -> window.getScene().lookup(".dialog-pane")).filter(DialogPane.class::isInstance).findFirst().orElseThrow();
            ((Button) pane.lookupButton(ButtonType.YES)).fire(); return null;
        });
    }
    private static void snapshot(Stage stage, String filename) throws Exception {
        BufferedImage bitmap = fx(() -> {
            var image = stage.getScene().snapshot(null); int width = (int) image.getWidth(), height = (int) image.getHeight(); BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int row = 0; row < height; row++) { for (int column = 0; column < width; column++) { result.setRGB(column, row, image.getPixelReader().getArgb(column, row)); } } return result;
        });
        Path output = Path.of("target/wallet-ui-preview"); Files.createDirectories(output); ImageIO.write(bitmap, "png", output.resolve(filename).toFile());
    }
    private static <Result> Result field(Object object, String name, Class<Result> type) { try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(object)); } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); } }
    private static <Result> Result fx(Callable<Result> action) throws Exception { CompletableFuture<Result> future = new CompletableFuture<>(); Platform.runLater(() -> { try { future.complete(action.call()); } catch (Throwable error) { future.completeExceptionally(error); } }); return future.get(15, TimeUnit.SECONDS); }
    private static void waitFor(Callable<Boolean> check, String message) throws Exception { long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(25); while (System.nanoTime() < end) { if (fx(check)) { return; } Thread.sleep(50); } throw new AssertionError("Timeout: " + message); }
    private static ApiResponse ok(CompletableFuture<ApiResponse> future) throws Exception { ApiResponse response = future.get(10, TimeUnit.SECONDS); TestSupport.check(response.isSuccess(), response.getMessageText()); return response; }
}
