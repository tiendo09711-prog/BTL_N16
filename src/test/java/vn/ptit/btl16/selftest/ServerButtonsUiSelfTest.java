package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TabPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.common.util.ConnectionCode;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.fx.ServerFxApp;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ServerButtonsUiSelfTest {
    private ServerButtonsUiSelfTest() { }
    public static void main(String[] args) throws Exception {
        var data = Files.createTempDirectory("btl16-server-buttons-");
        var config = data.resolve("server.properties");
        Files.writeString(config, "server.tcp.enabled=false\nserver.websocket.port=0\nmedia.port=0\n"
                + "db.driver=org.h2.Driver\ndb.name=auction\ndb.user=sa\ndb.password=\nsecurity.passwordIterations=10000\n");
        System.setProperty("btl16.server.config", config.toString()); System.setProperty("btl16.dataDir", data.toString());
        Platform.startup(() -> Platform.setImplicitExit(false));
        Map<DataFormat, Object> clipboard = fx(() -> {
            Map<DataFormat, Object> values = new HashMap<>(); Clipboard system = Clipboard.getSystemClipboard();
            for (DataFormat format : system.getContentTypes()) { values.put(format, system.getContent(format)); }
            return values;
        });
        ServerFxApp app = new ServerFxApp(); Stage stage = fx(() -> { Stage value = new Stage(); app.start(value); return value; });
        try {
            waitFor(() -> field(app, "application") != null && !(boolean) field(app, "busy"), "server dashboard starts");
            fx(() -> { ((Button)stage.getScene().lookup("#server-cash-flow-open")).fire(); return null; });
            waitFor(() -> Window.getWindows().stream().anyMatch(window -> window.isShowing() && window.getScene().lookup("#cash-flow-table")!=null),"cash-flow navigation opens server-only money page");
            fx(() -> {
                TestSupport.check(stage.getScene().lookup("#cash-flow-table") != null, "cash flow is embedded in the main server window");
                TestSupport.equals(1L, Window.getWindows().stream().filter(Window::isShowing).count(), "menu navigation creates no floating window");
                ((TabPane) stage.getScene().lookup("#server-admin-tabs")).getSelectionModel().selectFirst();
                return null;
            });
            fx(() -> {
                @SuppressWarnings("unchecked") ComboBox<String> addresses = (ComboBox<String>) field(app, "addresses");
                String selected = addresses.getValue(); TestSupport.check(selected != null, "a live address is selected");
                ((Button) field(app, "copyAddress")).fire();
                TestSupport.equals(selected, Clipboard.getSystemClipboard().getString(), "copy-address button writes actual endpoint");
                ((Button) field(app, "copyCode")).fire();
                TestSupport.equals(selected, ConnectionCode.decode(Clipboard.getSystemClipboard().getString()), "copy-code button round trips to endpoint");
                return null;
            });
            ServerApplication initial = fx(() -> (ServerApplication) field(app, "application"));
            try (var client = new WebSocketClientTransport("ws://127.0.0.1:" + initial.getWebSocketBoundPort() + "/ws", 2097152, 5000, 10000)) {
                client.connect().get(10, TimeUnit.SECONDS);
                TestSupport.check(new AccountApi(client).register("server_buttons", "Password123!", "Server Buttons", "", "").get(10, TimeUnit.SECONDS).isSuccess(), "real registration before pause");
            }
            Button toggle = fx(() -> (Button) field(app, "toggle")); Platform.runLater(toggle::fire);
            waitFor(() -> new ArrayList<>(Window.getWindows()).stream().anyMatch(window -> window.isShowing()
                    && window.getScene().getRoot().lookup(".dialog-pane") instanceof DialogPane), "pause button asks for confirmation");
            fx(() -> {
                DialogPane dialog = (DialogPane) new ArrayList<>(Window.getWindows()).stream().filter(Window::isShowing)
                        .map(window -> window.getScene().getRoot().lookup(".dialog-pane")).filter(DialogPane.class::isInstance).findFirst().orElseThrow();
                ((Button) dialog.lookupButton(ButtonType.YES)).fire(); return null;
            });
            waitFor(() -> field(app, "application") == null && !(boolean) field(app, "busy"), "pause button stops the server");
            fx(() -> {
                ((Button) stage.getScene().lookup("#server-cash-flow-open")).fire();
                TestSupport.check(stage.getScene().lookup("#server-offline-banner").isVisible(), "paused server displays an inline notice");
                TestSupport.check(stage.getScene().lookup("#server-cash-flow").isDisabled(), "admin actions are disabled while paused");
                return null;
            });
            fx(() -> { toggle.fire(); return null; });
            waitFor(() -> field(app, "application") != null && !(boolean) field(app, "busy"), "start button restarts the server");
            fx(() -> {
                TestSupport.check(!stage.getScene().lookup("#server-offline-banner").isVisible(), "restart clears the inline notice");
                TestSupport.check(!stage.getScene().lookup("#server-cash-flow").isDisabled(), "restart reactivates the selected admin page");
                TestSupport.equals(1L, Window.getWindows().stream().filter(Window::isShowing).count(), "restart keeps a single admin window");
                return null;
            });
            ServerApplication restarted = fx(() -> (ServerApplication) field(app, "application"));
            try (var client = new WebSocketClientTransport("ws://127.0.0.1:" + restarted.getWebSocketBoundPort() + "/ws", 2097152, 5000, 10000)) {
                client.connect().get(10, TimeUnit.SECONDS);
                TestSupport.check(new AccountApi(client).login("server_buttons", "Password123!").get(10, TimeUnit.SECONDS).isSuccess(), "restart button preserves real account data");
            }
            System.out.println("[PASS] ServerButtonsUiSelfTest: actual copy-address/copy-code clipboard, pause confirmation, stop/start buttons and persistence");
        } finally {
            fx(() -> { app.stop(); stage.hide(); Clipboard.getSystemClipboard().setContent(clipboard); return null; }); Platform.exit();
        }
    }
    private static Object field(Object target, String name) throws Exception { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static <Result> Result fx(Callable<Result> action) throws Exception { var result = new CompletableFuture<Result>(); Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } }); return result.get(20, TimeUnit.SECONDS); }
    private static void waitFor(Callable<Boolean> condition, String description) throws Exception { long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20); while (System.nanoTime() < deadline) { if (fx(condition)) { return; } Thread.sleep(50); } throw new AssertionError("Timeout: " + description); }
}
