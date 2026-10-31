package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.ptit.btl16.client.fx.FxClientController;
import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.network.ConnectionState;
import vn.ptit.btl16.client.network.ConnectionStateListener;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.WireMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class LoginFeedbackUiSelfTest {
    private record View(Stage stage, FxClientController controller, ClientAppModel model, Network network, Scene scene) {
        TextField username() { return (TextField) scene.lookup("#login-username"); }
        TextField password() { return (TextField) scene.lookup("#login-password"); }
        Button submit() { return (Button) scene.lookup("#login-submit"); }
        HBox banner() { return (HBox) scene.lookup("#login-error-banner"); }
        Label message() { return (Label) scene.lookup("#login-error-message"); }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("btl16.client.sound.disabled", "true");
        System.setProperty("prism.order", "sw");
        AtomicReference<Throwable> uiFailure = new AtomicReference<>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> uiFailure.compareAndSet(null, error));
        });
        View view = null;
        try {
            view = fx(() -> {
                Network network = new Network();
                ClientAppModel model = new ClientAppModel();
                Stage stage = new Stage();
                FxClientController controller = new FxClientController(stage, ClientConfig.loadDefault(), model,
                        network, new AccountApi(network), new AuctionApi(network));
                invoke(controller, "buildScenes");
                var field = controller.getClass().getDeclaredField("loginScene"); field.setAccessible(true);
                Scene scene = (Scene) field.get(controller);
                stage.setScene(scene); stage.show(); scene.getRoot().applyCss(); scene.getRoot().layout();
                return new View(stage, controller, model, network, scene);
            });
            View current = view;
            fx(() -> {
                TestSupport.check(!current.banner().isVisible() && !current.banner().isManaged(), "no empty error space initially");
                current.submit().fire();
                feedback(current, true);
                TestSupport.equals(0, current.network().loginRequests, "empty credentials stay local");
                current.username().setText("buyer"); current.password().setText("wrong-password");
                TestSupport.check(!current.banner().isVisible(), "editing clears feedback");
                current.submit().fire();
                TestSupport.check(current.submit().isDisabled() && current.password().isDisabled(), "pending login disables credentials and submit");
                invoke(current.controller(), "login");
                TestSupport.equals(1, current.network().loginRequests, "repeated submission does not duplicate a pending request");
                current.network().reply("INVALID_CREDENTIALS", "Technical server detail");
                return null;
            });
            fx(() -> {
                feedback(current, true);
                TestSupport.check(!current.message().getText().contains("INVALID_CREDENTIALS")
                        && !current.message().getText().contains("Technical"), "invalid credentials use friendly copy without technical code");
                TestSupport.equals("wrong-password", current.password().getSelectedText(), "password is selected for retry");
                TestSupport.check(!current.submit().isDisabled(), "failure restores submit");
                snapshot(current.scene());
                current.password().setText("retry-password");
                TestSupport.check(!current.banner().isManaged(), "editing collapses feedback");
                TestSupport.check(!current.username().getPseudoClassStates().contains(PseudoClass.getPseudoClass("invalid")), "editing clears field borders");
                current.submit().fire();
                current.network().pending.completeExceptionally(new IllegalStateException("Technical connection error"));
                return null;
            });
            fx(() -> {
                feedback(current, false);
                TestSupport.check(!current.message().getText().contains("Technical"), "transport failure has friendly copy");
                current.submit().fire(); current.network().reply("SERVER_ERROR", "");
                return null;
            });
            fx(() -> {
                feedback(current, false);
                TestSupport.check(!current.message().getText().isBlank(), "empty server message has fallback copy");
                current.submit().fire(); current.network().reply("ACCOUNT_DISABLED", "Account temporarily unavailable");
                return null;
            });
            fx(() -> {
                feedback(current, false);
                TestSupport.equals("Tài khoản đã bị khóa. Vui lòng liên hệ người quản lý.", current.message().getText(), "English server messages are translated without error codes");
                current.submit().fire(); current.network().reply("ACCOUNT_DISABLED", "Tài khoản tạm thời không khả dụng. Vui lòng liên hệ hỗ trợ.");
                return null;
            });
            fx(() -> {
                feedback(current, false);
                TestSupport.equals("Tài khoản tạm thời không khả dụng. Vui lòng liên hệ hỗ trợ.", current.message().getText(), "useful Vietnamese server detail is preserved");
                current.submit().fire();
                current.network().pending.complete(WireMessage.response(MessageType.LOGIN_RESULT, null, 0,
                        Map.of("success", "true", "userId", "2", "username", "buyer", "sessionToken", "test-session")));
                return null;
            });
            fx(() -> {
                TestSupport.check(current.stage().getScene() != current.scene() && current.model().hasSessionToken(), "retry can log in successfully");
                TestSupport.check(current.password().getText().isEmpty() && !current.banner().isVisible(), "success clears password and feedback");
                TestSupport.check(uiFailure.get() == null, "no asynchronous JavaFX failures: " + uiFailure.get());
                return null;
            });
            System.out.println("[PASS] LoginFeedbackUiSelfTest: inline validation, retry, no modal errors, pending guard, network/server failures and successful login");
        } finally {
            View closing = view;
            fx(() -> { if (closing != null) { closing.controller().close(); closing.stage().close(); } return null; });
            Platform.exit();
        }
    }

    private static void feedback(View view, boolean invalid) {
        TestSupport.check(view.banner().isVisible() && view.banner().isManaged(), "login feedback is inline and visible");
        TestSupport.equals(1, Window.getWindows().size(), "failure never opens a popup");
        TestSupport.check(view.stage().getScene() == view.scene(), "failure stays on the login scene");
        for (TextField field : new TextField[]{view.username(), view.password()}) {
            TestSupport.equals(invalid, field.getPseudoClassStates().contains(PseudoClass.getPseudoClass("invalid")), "only credential failures highlight fields");
        }
    }

    private static void snapshot(Scene scene) throws Exception {
        scene.getRoot().applyCss(); scene.getRoot().layout();
        var image = scene.getRoot().snapshot(null, null);
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
        var output = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        output.setRGB(0, 0, width, height, pixels, 0, width);
        Path directory = Path.of("target", "login-feedback-ui"); Files.createDirectories(directory);
        javax.imageio.ImageIO.write(output, "png", directory.resolve("login-invalid.png").toFile());
    }

    private static void invoke(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(target);
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } });
        return result.get(15, TimeUnit.SECONDS);
    }

    private static final class Network implements ClientTransport {
        private CompletableFuture<WireMessage> pending;
        private int loginRequests;
        void reply(String code, String message) {
            pending.complete(WireMessage.response(MessageType.LOGIN_RESULT, null, 0,
                    Map.of("success", "false", "errorCode", code, "message", message)));
        }
        @Override public CompletableFuture<WireMessage> sendRequest(MessageType type, Map<String, String> data) {
            if (type == MessageType.LOGIN) { loginRequests++; pending = new CompletableFuture<>(); return pending; }
            return CompletableFuture.completedFuture(WireMessage.response(type, null, 0, Map.of("success", "true", "count", "0", "balance", "0", "available", "0")));
        }
        @Override public CompletableFuture<Void> connect() { return CompletableFuture.completedFuture(null); }
        @Override public void disconnect() { }
        @Override public boolean isConnected() { return true; }
        @Override public ConnectionState getState() { return ConnectionState.CONNECTED; }
        @Override public String getEndpoint() { return "ws://127.0.0.1:8890/ws"; }
        @Override public void setEndpoint(String endpoint) { }
        @Override public CompletableFuture<WireMessage> ping() { return sendRequest(MessageType.PING, Map.of()); }
        @Override public void addEventListener(Consumer<WireMessage> listener) { }
        @Override public void addStateListener(ConnectionStateListener listener) { }
        @Override public void close() { }
    }
}
