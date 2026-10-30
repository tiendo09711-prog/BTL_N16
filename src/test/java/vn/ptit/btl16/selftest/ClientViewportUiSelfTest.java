package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.ptit.btl16.client.fx.FxClientController;
import vn.ptit.btl16.client.fx.AuctionRankingWindow;
import vn.ptit.btl16.client.fx.AuctionReceiptsPane;
import vn.ptit.btl16.client.fx.AuctionRulesWindow;
import vn.ptit.btl16.client.fx.PersonalNotificationsPane;
import vn.ptit.btl16.client.fx.ProductMediaEditor;
import vn.ptit.btl16.client.fx.WalletPane;
import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.model.ClientProduct;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.client.service.ProductMediaApi;
import vn.ptit.btl16.client.service.WalletApi;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.fx.DesktopTheme;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class ClientViewportUiSelfTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("btl16.client.sound.disabled", "true");
        System.setProperty("prism.order", System.getProperty("prism.order", "sw"));
        Platform.startup(() -> Platform.setImplicitExit(false));
        try (var transport = new WebSocketClientTransport("ws://127.0.0.1:1/ws", 2097152, 1000, 1000)) {
            Stage owner = fx(() -> {
                Stage stage = new Stage(); stage.setScene(new Scene(new VBox(), 1100, 640)); stage.show(); return stage;
            });
            creation(owner);
            genericDialogs(owner);
            productEditor(owner);
            standalone(owner, transport);
            shell(owner, transport);
            System.out.println("[PASS] Client viewports keep actions reachable and provide back navigation at compact heights");
        } finally {
            fx(() -> { for (Window window : List.copyOf(Window.getWindows())) { window.hide(); } return null; });
            Platform.exit();
        }
    }

    private static void creation(Stage owner) throws Exception {
        Dialog<?> dialog = fx(() -> {
            var type = Class.forName("vn.ptit.btl16.client.fx.AuctionCreationDialog");
            var constructor = type.getDeclaredConstructor(Stage.class, List.class, Supplier.class); constructor.setAccessible(true);
            Instant now = Instant.now();
            var product = new ClientProduct(1, 1, "seller", "MH1", "Xe may", "San pham de kiem thu", true, now, now);
            var result = (Dialog<?>) constructor.newInstance(owner, List.of(product), (Supplier<Instant>) () -> now);
            result.show(); return result;
        });
        drain();
        fx(() -> {
            Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
            var screen = Screen.getScreensForRectangle(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight()).getFirst().getVisualBounds();
            TestSupport.check(stage.getY() >= screen.getMinY() && stage.getY() + stage.getHeight() <= screen.getMaxY() + 1,
                    "creation initially fits the work area without hiding its footer");
            return null;
        });
        for (int height : new int[]{650, 560, 480}) {
            compact((Stage) dialog.getDialogPane().getScene().getWindow(), 580, height);
            fx(() -> {
                var pane = dialog.getDialogPane(); layout(pane.getScene());
                visible(pane.lookup("#auction-create-next")); visible(pane.lookup("#auction-create-cancel"));
                var scroll = (ScrollPane) pane.lookup("#auction-create-scroll");
                TestSupport.check(scroll.getContent().getLayoutBounds().getHeight() > scroll.getViewportBounds().getHeight(),
                        "creation retains all fields and overflows inside its scroll viewport at height " + height);
                scroll.setVvalue(1); layout(pane.getScene()); visible(pane.lookup("#auction-create-identity"));
                if (height == 480) { screenshot(pane.getScene(), "creation-480"); }
                return null;
            });
        }
        fx(() -> {
            var pane = dialog.getDialogPane(); ((TextField) pane.lookup("#auction-create-price")).setText("123456");
            ((Button) pane.lookup("#auction-create-next")).fire(); return null;
        });
        compact((Stage) dialog.getDialogPane().getScene().getWindow(), 640, 480);
        fx(() -> {
            var pane = dialog.getDialogPane(); layout(pane.getScene());
            visible(pane.lookup("#auction-create-confirm")); visible(pane.lookup("#auction-create-back"));
            screenshot(pane.getScene(), "review-480");
            ((Button) pane.lookup("#auction-create-back")).fire();
            TestSupport.equals("123456", ((TextField) pane.lookup("#auction-create-price")).getText(), "back from review preserves the draft");
            return null;
        });
        compact((Stage) dialog.getDialogPane().getScene().getWindow(), 580, 480);
        fx(() -> {
            var pane = dialog.getDialogPane(); var price = (TextField) pane.lookup("#auction-create-price"); price.setText("invalid");
            ((Button) pane.lookup("#auction-create-next")).fire(); return null;
        });
        drain();
        fx(() -> {
            var pane = dialog.getDialogPane(); layout(pane.getScene());
            visible(pane.lookup("#auction-create-error")); visible(pane.lookup("#auction-create-next"));
            var scroll = (ScrollPane) pane.lookup("#auction-create-scroll");
            for (double position : new double[]{0, .5, 1}) {
                scroll.setVvalue(position); layout(pane.getScene()); visible(pane.lookup("#auction-create-error"));
            }
            screenshot(pane.getScene(), "validation-480");
            ((Button) pane.lookup("#auction-create-cancel")).fire();
            TestSupport.check(!dialog.isShowing(), "back from editing returns to the owner"); return null;
        });
    }

    private static void genericDialogs(Stage owner) throws Exception {
        Dialog<Void> dialog = fx(() -> {
            Dialog<Void> result = new Dialog<>(); DesktopTheme.dialog(result, owner);
            VBox form = new VBox(10);
            for (int index = 0; index < 24; index++) { form.getChildren().add(new TextField("Field " + index)); }
            form.setMinHeight(Region.USE_PREF_SIZE); result.getDialogPane().setContent(form);
            result.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL); result.show(); return result;
        });
        compact((Stage) dialog.getDialogPane().getScene().getWindow(), 520, 440);
        fx(() -> {
            var pane = dialog.getDialogPane(); layout(pane.getScene()); visible(pane.lookupButton(ButtonType.OK));
            TestSupport.check(pane.getContent() instanceof ScrollPane, "long plain forms get a shared content-only scroll container");
            ScrollPane scroll = (ScrollPane) pane.getContent(); scroll.setVvalue(1); layout(pane.getScene());
            visible(((VBox) scroll.getContent()).getChildren().getLast()); dialog.close(); return null;
        });
        Dialog<Void> profile = fx(() -> {
            var type = Class.forName("vn.ptit.btl16.client.fx.AccountProfileForm");
            var constructor = type.getDeclaredConstructor(ClientAppModel.class, Runnable.class); constructor.setAccessible(true);
            Node form = (Node) constructor.newInstance(new ClientAppModel(), (Runnable) () -> { });
            ScrollPane scroll = new ScrollPane(form); scroll.setFitToWidth(true);
            VBox body = new VBox(new Label("Profile"), scroll); body.setPrefHeight(590); VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);
            Dialog<Void> result = new Dialog<>(); result.getDialogPane().setContent(body);
            result.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL); DesktopTheme.dialog(result, owner); result.show(); return result;
        });
        compact((Stage) profile.getDialogPane().getScene().getWindow(), 520, 480);
        fx(() -> {
            layout(profile.getDialogPane().getScene()); visible(profile.getDialogPane().lookupButton(ButtonType.OK));
            TestSupport.check(profile.getDialogPane().getContent() instanceof VBox, "existing vertical scrolls are reused without nesting another form scroll");
            profile.close(); return null;
        });
        Alert alert = fx(() -> {
            Alert result = new Alert(Alert.AlertType.INFORMATION, "A long message must remain readable without hiding the actions. ".repeat(120));
            DesktopTheme.dialog(result, owner); result.show(); return result;
        });
        compact((Stage) alert.getDialogPane().getScene().getWindow(), 520, 480);
        fx(() -> {
            var pane = alert.getDialogPane(); layout(pane.getScene()); visible(pane.lookupButton(ButtonType.OK));
            TestSupport.check(pane.getContentText() != null && pane.getContentText().startsWith("A long message"), "scrollable alerts preserve their content-text API");
            TestSupport.check(pane.getContent() instanceof ScrollPane, "long alert text gets a content viewport");
            var scroll = (ScrollPane) pane.getContent();
            TestSupport.check(scroll.getContent().getLayoutBounds().getHeight() > scroll.getViewportBounds().getHeight(), "long alert text overflows inside its viewport");
            alert.close(); return null;
        });
    }

    private static void productEditor(Stage owner) throws Exception {
        Stage stage = fx(() -> {
            var editor = new ProductMediaEditor(owner, new ProductMediaApi(URI.create("http://127.0.0.1:1/"), () -> ""), null, () -> { });
            Stage window = (Stage) field(editor, "stage"); window.show(); return window;
        });
        compact(stage, 1000, 480);
        fx(() -> {
            layout(stage.getScene()); visible(stage.getScene().lookup("#product-editor-save")); visible(stage.getScene().lookup("#product-editor-back"));
            TestSupport.check(stage.getScene().lookup("#product-editor-preview-scroll") instanceof ScrollPane, "media editing tools have a scroll fallback");
            ((Button) stage.getScene().lookup("#product-editor-back")).fire();
            TestSupport.check(!stage.isShowing(), "product back closes the editor"); return null;
        });
    }

    private static void shell(Stage owner, WebSocketClientTransport transport) throws Exception {
        FxClientController controller = fx(() -> {
            var model = new ClientAppModel();
            var result = new FxClientController(owner, ClientConfig.loadDefault(), model, transport, new AccountApi(transport), new AuctionApi(transport));
            invoke(result, "buildScenes"); owner.setScene((Scene) field(result, "loginScene"));
            DesktopTheme.fitOnScreen(owner, 1120, 780); return result;
        });
        try {
            compact(owner, 1100, 480);
            fx(() -> {
                var scroll = (ScrollPane) owner.getScene().lookup("#client-login-scroll");
                TestSupport.check(scroll != null, "login has a scroll fallback");
                scroll.setVvalue(1); layout(owner.getScene());
                TestSupport.check(scroll.getContent().getLayoutBounds().getHeight() > scroll.getViewportBounds().getHeight(), "login keeps its entire form on compact displays");
                owner.setScene((Scene) field(controller, "mainScene")); DesktopTheme.fitOnScreen(owner, 1420, 880); return null;
            });
            compact(owner, 1100, 480);
            fx(() -> {
                ScrollPane navigation = (ScrollPane) owner.getScene().lookup("#client-navigation-scroll"); navigation.setVvalue(1); layout(owner.getScene());
                visible(owner.getScene().lookup("#marketplace-logout"));
                TestSupport.check(navigation.getContent().getLayoutBounds().getHeight() > navigation.getViewportBounds().getHeight(),
                        "sidebar actions scroll instead of falling off screen: content=" + navigation.getContent().getLayoutBounds()
                                + ", viewport=" + navigation.getViewportBounds() + ", scene=" + owner.getScene().getHeight());
                invoke(controller, "showRules"); layout(owner.getScene());
                var back = (Button) owner.getScene().lookup("#client-page-back"); visible(back); TestSupport.check(!back.isDisabled(), "back is enabled on account pages");
                back.fire(); TestSupport.check(!back.isVisible(), "back returns from rules to the marketplace");
                invoke(controller, "showTransactions"); layout(owner.getScene()); visible(back);
                reveal(owner.getScene().lookup("#transactions-next"));
                screenshot(owner.getScene(), "transactions-480");
                back.fire(); TestSupport.check(!back.isVisible(), "back returns from transactions to the marketplace");
                return null;
            });
        } finally {
            fx(() -> { controller.close(); return null; });
        }
    }

    private static void standalone(Stage owner, WebSocketClientTransport transport) throws Exception {
        Object[] windows = fx(() -> new Object[]{
                new WalletPane(owner, new WalletApi(transport), 1, "seller", () -> true),
                new AuctionReceiptsPane(owner, transport, () -> true),
                new PersonalNotificationsPane(owner, transport, () -> true, (kind, room) -> { }),
                new AuctionRankingWindow(owner, transport, () -> true),
                new AuctionRulesWindow(owner)
        });
        String[] actions = {"wallet-confirm", "receipts-next", "notifications-next", "revenue-ranking-refresh", "auction-rules-close"};
        for (int index = 0; index < windows.length; index++) {
            Object view = windows[index]; String action = actions[index];
            Stage stage = fx(() -> { var window = (Stage) field(view, "stage"); window.show(); return window; });
            compact(stage, 1000, 480);
            fx(() -> {
                Node target = stage.getScene().lookup("#" + action); reveal(target);
                screenshot(stage.getScene(), action + "-480"); stage.close(); return null;
            });
        }
    }

    private static void reveal(Node target) {
        TestSupport.check(target != null, "target action exists");
        for (int attempt = 0; attempt < 2; attempt++) {
            for (Node parent = target.getParent(); parent != null; parent = parent.getParent()) {
                if (parent instanceof ScrollPane scroll) {
                    Bounds content = scroll.getContent().getLayoutBounds();
                    Bounds bounds = scroll.getContent().sceneToLocal(target.localToScene(target.getBoundsInLocal()));
                    double viewportHeight = scroll.getViewportBounds().getHeight();
                    double overflow = content.getHeight() - viewportHeight;
                    if (overflow > 0) {
                        double offset = bounds.getMinY() - content.getMinY() - (viewportHeight - bounds.getHeight()) / 2;
                        scroll.setVvalue(scroll.getVmin() + Math.clamp(offset / overflow, 0, 1) * (scroll.getVmax() - scroll.getVmin()));
                    }
                    layout(target.getScene());
                }
            }
            layout(target.getScene());
        }
        visible(target);
    }

    private static void compact(Stage stage, double width, double height) throws Exception {
        drain();
        fx(() -> { stage.setMinHeight(0); stage.setMinWidth(0); stage.setWidth(width); stage.setHeight(height); return null; });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            boolean settled = fx(() -> {
                stage.setWidth(width); stage.setHeight(height); layout(stage.getScene());
                return stage.getScene().getHeight() > 0 && stage.getScene().getHeight() <= height + 1
                        && stage.getScene().getRoot().getLayoutBounds().getHeight() <= height + 1;
            });
            if (settled) { drain(); return; }
            Thread.sleep(40);
        }
        throw new AssertionError(fx(() -> "window and scene must settle at the requested compact height: stage="
                + stage.getWidth() + "x" + stage.getHeight() + ", scene=" + stage.getScene().getWidth() + "x" + stage.getScene().getHeight()
                + ", root=" + stage.getScene().getRoot().getLayoutBounds()));
    }

    private static void visible(Node node) {
        TestSupport.check(node != null && node.isVisible(), "control exists and is visible");
        Bounds bounds = node.localToScene(node.getBoundsInLocal());
        TestSupport.check(bounds.getMinY() >= -1 && bounds.getMaxY() <= node.getScene().getHeight() + 1,
                "control stays inside the scene: " + node.getId() + " " + bounds + " / " + node.getScene().getHeight());
        for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent.getStyleClass().contains("viewport")) {
                Bounds viewport = parent.localToScene(parent.getLayoutBounds());
                TestSupport.check(bounds.getMinY() >= viewport.getMinY() - 1 && bounds.getMaxY() <= viewport.getMaxY() + 1,
                        "control is not clipped by a scroll viewport: " + node.getId() + " " + bounds + " / " + viewport);
            }
        }
    }

    private static void layout(Scene scene) { scene.getRoot().applyCss(); scene.getRoot().layout(); }
    private static void screenshot(Scene scene, String name) throws Exception {
        var directory = java.nio.file.Path.of("target", "client-viewport-audit"); java.nio.file.Files.createDirectories(directory);
        var snapshot = scene.getRoot().snapshot(null, null);
        int width = (int) snapshot.getWidth(), height = (int) snapshot.getHeight();
        int[] pixels = new int[width * height];
        snapshot.getPixelReader().getPixels(0, 0, width, height, javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
        var image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width); javax.imageio.ImageIO.write(image, "png", directory.resolve(name + ".png").toFile());
    }
    private static Object field(Object target, String name) throws Exception { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static void invoke(Object target, String name) throws Exception { var method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(target); }
    private static void drain() throws Exception { Thread.sleep(90); fx(() -> null); }
    private static <T> T fx(Callable<T> action) throws Exception {
        var future = new CompletableFuture<T>(); Platform.runLater(() -> { try { future.complete(action.call()); } catch (Throwable error) { future.completeExceptionally(error); } });
        return future.get(15, TimeUnit.SECONDS);
    }
}
