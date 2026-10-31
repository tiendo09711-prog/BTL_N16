package vn.ptit.btl16.selftest;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.common.fx.DesktopTheme;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class DialogVisualUiSelfTest {
    private static final boolean BASELINE = Boolean.getBoolean("btl16.audit.baseline");
    private static final Path OUTPUT = Path.of("target/client-visual-audit", BASELINE ? "before" : "after");
    private static final List<Map<String, Object>> SCREENS = new ArrayList<>();
    private static final java.util.concurrent.atomic.AtomicReference<Throwable> UI_FAILURE = new java.util.concurrent.atomic.AtomicReference<>();

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT); Platform.startup(() -> {
            Platform.setImplicitExit(false);
            Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> UI_FAILURE.compareAndSet(null, failure));
        });
        try {
            Stage owner = fx(() -> {
                Stage stage = new Stage(); Scene scene = new Scene(new VBox(), 1100, 700);
                DesktopTheme.apply(scene); stage.setScene(scene); stage.show(); return stage;
            });
            for (Alert.AlertType type : List.of(Alert.AlertType.CONFIRMATION, Alert.AlertType.INFORMATION, Alert.AlertType.WARNING, Alert.AlertType.ERROR)) {
                Dialog<?> dialog = fx(() -> {
                    Alert alert = new Alert(type, "Đặt cọc 1.000.000 đ cho phòng #10?\n"
                            + "Tiền được giữ trong ví, tính vào tiền đấu giá; nếu không thắng sẽ hoàn khi phiên kết thúc hoặc hủy.\n"
                            + "Cọc phải hoàn tất trước 30/10/2026 21:02:00. Cọc không giữ chỗ trong phòng.", ButtonType.OK, ButtonType.CANCEL);
                    if (type == Alert.AlertType.CONFIRMATION) { alert.setTitle("Xác nhận đặt cọc"); }
                    alert.setHeaderText("Ducati Panigale V4 — phiên đấu giá #10");
                    DesktopTheme.dialog(alert, owner); alert.show(); return alert;
                });
                capture(dialog, "alert-" + type.name().toLowerCase(Locale.ROOT), 580, 0);
                if (!BASELINE) { fx(() -> {
                    var pane = dialog.getDialogPane();
                    TestSupport.check(pane.getStyleClass().contains("desktop-dialog"), "shared dialog surface is applied");
                    TestSupport.check(pane.getHeader() instanceof Label && pane.getGraphic() == null, "meaningful alert header stays plain without an icon");
                    TestSupport.check(((Label) pane.getHeader()).getFont().getSize() <= 13, "alert header uses regular text size");
                    TestSupport.check(pane.lookup(".dialog-title") == null && pane.lookup(".dialog-eyebrow") == null, "no large title or decorative caption");
                    String title = switch (type) {
                        case ERROR -> "Lỗi";
                        case WARNING -> "Lưu ý";
                        case INFORMATION -> "Thông báo";
                        default -> "Xác nhận đặt cọc";
                    };
                    TestSupport.equals(title, dialog.getTitle(), "alert window title is Vietnamese");
                    TestSupport.equals(type == Alert.AlertType.CONFIRMATION ? "Xác nhận" : "Đã hiểu",
                            ((Button) pane.lookupButton(ButtonType.OK)).getText(), "localized button keeps the original ButtonType"); return null;
                }); }
                capture(dialog, "compact-" + type.name().toLowerCase(Locale.ROOT), 420, 400); fx(() -> {
                    ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).fire();
                    TestSupport.equals(ButtonType.CANCEL, dialog.getResult(), "localized cancellation preserves the result contract"); return null;
                });
            }
            Dialog<?> message = fx(() -> {
                Alert dialog = new Alert(Alert.AlertType.INFORMATION, "Thông tin phiên đấu giá và điều kiện tham gia. ".repeat(180));
                dialog.setHeaderText("Thông tin chi tiết"); DesktopTheme.dialog(dialog, owner); dialog.show(); return dialog;
            });
            capture(message, "long-message", 520, 440);
            fx(() -> {
                TestSupport.check(message.getDialogPane().getContentText().length() > 1000, "message content API is retained");
                ((ScrollPane) message.getDialogPane().getContent()).setVvalue(1); return null;
            });
            capture(message, "long-message-bottom", 520, 440); fx(() -> { message.close(); return null; });
            Dialog<?> input = fx(() -> {
                TextInputDialog dialog = new TextInputDialog("60"); dialog.setTitle("Gia hạn phiên đấu giá");
                dialog.setHeaderText("Số giây gia hạn (10–600)"); dialog.setContentText("Thời gian bổ sung");
                DesktopTheme.dialog(dialog, owner); dialog.show(); return dialog;
            });
            capture(input, "text-input", 520, 0); fx(() -> {
                ((TextInputDialog) input).getEditor().setText("120");
                if (!BASELINE) { TestSupport.check(input.getDialogPane().getStyleClass().contains("standard-dialog"), "text input uses the shared surface"); }
                ((Button) input.getDialogPane().lookupButton(ButtonType.OK)).fire();
                TestSupport.equals("120", input.getResult(), "styled text input preserves the edited result"); return null;
            });
            Dialog<?> choice = fx(() -> {
                ChoiceDialog<String> dialog = new ChoiceDialog<>("nguyenvana", "nguyenvana", "tranthib");
                dialog.setTitle("Quản lý người tham gia"); dialog.setHeaderText("Chọn người chưa đặt giá để mời ra khỏi phòng");
                dialog.setContentText("Người tham gia"); DesktopTheme.dialog(dialog, owner); dialog.show(); return dialog;
            });
            capture(choice, "choice-input", 520, 0); fx(() -> {
                if (!BASELINE) { TestSupport.check(choice.getDialogPane().getStyleClass().contains("standard-dialog"), "choice input uses the shared surface"); }
                ((Button) choice.getDialogPane().lookupButton(ButtonType.OK)).fire();
                TestSupport.equals("nguyenvana", choice.getResult(), "styled choice input preserves the selected result"); return null;
            });
            Dialog<?> form = fx(() -> {
                Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Biểu mẫu nhiều trường"); VBox fields = new VBox(12);
                for (int index = 0; index < 12; index++) {
                    TextField field = new TextField("Nội dung " + (index + 1)); field.setId("audit-field-" + index);
                    fields.getChildren().add(new VBox(6, DesktopTheme.label("Trường thông tin " + (index + 1), "muted"), field));
                }
                dialog.getDialogPane().setContent(fields); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
                DesktopTheme.dialog(dialog, owner); dialog.show(); return dialog;
            });
            capture(form, "long-form", 520, 440); fx(() -> { ((ScrollPane) form.getDialogPane().getContent()).setVvalue(1); return null; });
            capture(form, "long-form-bottom", 520, 440); fx(() -> { form.close(); return null; });
            if (!BASELINE) { deposit(owner); controls(owner); controllerDialogs(); }
            fx(() -> { TestSupport.check(UI_FAILURE.get() == null, "no asynchronous UI failures: " + UI_FAILURE.get()); return null; });
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(OUTPUT.resolve("screens.json").toFile(), SCREENS);
            System.out.println("[PASS] DialogVisualUiSelfTest: " + SCREENS.size() + " native screens, compact footers, localized buttons and input dialogs");
        } finally {
            fx(() -> { for (Window window : List.copyOf(Window.getWindows())) { window.hide(); } return null; }); Platform.exit();
        }
    }

    private static void deposit(Stage owner) throws Exception {
        Dialog<?> dialog = fx(() -> {
            var room = ClientWireParser.auction(Map.of("auctionId", "10", "productName", "Ducati Panigale V4", "depositAmount", "1000000", "startTime", "2026-10-30T14:02:00Z"), "");
            var type = Class.forName("vn.ptit.btl16.client.fx.RoomDepositDialog");
            var constructor = type.getDeclaredConstructor(Window.class, room.getClass()); constructor.setAccessible(true);
            var result = (Dialog<?>) constructor.newInstance(owner, room); result.show(); return result;
        });
        capture(dialog, "room-deposit", 580, 0);
        fx(() -> {
            for (String id : List.of("deposit-amount", "deposit-deadline", "deposit-policy")) {
                TestSupport.check(dialog.getDialogPane().lookup("#" + id) != null, "deposit has a distinct " + id + " section");
            }
            TestSupport.check(dialog.getDialogPane().getHeader() == null, "deposit does not duplicate the window title with a decorative header");
            TestSupport.check(dialog.getDialogPane().lookup(".dialog-title") == null
                    && dialog.getDialogPane().lookup(".dialog-eyebrow") == null, "deposit has no oversized title or uppercase caption");
            TestSupport.check(dialog.getDialogPane().getButtonTypes().contains(ButtonType.OK), "deposit result contract stays unchanged"); return null;
        });
        capture(dialog, "room-deposit-compact", 420, 480);
        fx(() -> { ((ScrollPane) dialog.getDialogPane().getContent()).setVvalue(1); return null; });
        capture(dialog, "room-deposit-compact-bottom", 420, 480); fx(() -> { dialog.close(); return null; });
    }

    private static void controls(Stage owner) throws Exception {
        fx(() -> {
            VBox form = new VBox(14, DesktopTheme.label("Thông tin sản phẩm", "section-title"), new TextField("Ducati Panigale V4"),
                    new ComboBox<>(javafx.collections.FXCollections.observableArrayList("Xe máy", "Đồ điện tử")),
                    new DatePicker(java.time.LocalDate.of(2026, 10, 30)), new Spinner<Integer>(1, 100, 10),
                    new CheckBox("Cho phép tham gia ẩn danh"), new RadioButton("Phòng công khai"), new TextArea("Mô tả sản phẩm và điều kiện tham gia."));
            form.getStyleClass().add("card"); form.setMinWidth(0);
            ((ComboBox<?>) form.getChildren().get(2)).getSelectionModel().selectFirst(); ((TextArea) form.getChildren().getLast()).setPrefRowCount(3);
            VBox list = new VBox(14, DesktopTheme.label("Danh sách và trạng thái", "section-title"),
                    new ListView<>(javafx.collections.FXCollections.observableArrayList("Nguyễn Văn An · Chủ phòng", "Trần Thị Bình · Người tham gia", "Phạm Minh Châu · Đang theo dõi")),
                    new TitledPane("Điều kiện tham gia", DesktopTheme.label("Cần hoàn tất đặt cọc trước giờ bắt đầu.", "muted")), new ProgressBar(.65), new Slider(0, 100, 55));
            list.getStyleClass().add("card"); list.setMinWidth(0); HBox columns = new HBox(20, form, list);
            HBox.setHgrow(form, Priority.ALWAYS); HBox.setHgrow(list, Priority.ALWAYS);
            TabPane tabs = new TabPane(new Tab("Tổng quan", columns), new Tab("Lịch sử giao dịch", DesktopTheme.label("Chưa có giao dịch.", "muted")));
            tabs.getTabs().forEach(tab -> tab.setClosable(false));
            Button confirm = new Button("Xác nhận"); confirm.getStyleClass().add("teal");
            VBox page = new VBox(20, DesktopTheme.label("Thành phần giao diện dùng chung", "page-title"), tabs, new HBox(10, confirm, new Button("Quay lại")));
            page.setStyle("-fx-padding: 28;"); VBox.setVgrow(tabs, Priority.ALWAYS);
            Scene scene = new Scene(page, 1060, 760); DesktopTheme.apply(scene); DesktopTheme.apply(scene);
            TestSupport.equals(2, scene.getStylesheets().size(), "theme application is idempotent");
            owner.setScene(scene); DesktopTheme.fitOnScreen(owner, 1100, 820); return null;
        });
        settle(); fx(() -> {
            captureScene(owner.getScene(), "shared-controls");
            TestSupport.check(owner.getScene().lookup(".progress-bar").getLayoutBounds().getHeight() >= 8, "progress stays visible inside a form card"); return null;
        });
    }

    private static void controllerDialogs() throws Exception {
        System.setProperty("btl16.client.manualConnect", "true");
        var calls = new ArrayList<vn.ptit.btl16.common.protocol.MessageType>();
        var transportType = vn.ptit.btl16.client.network.ClientTransport.class;
        var transport = (vn.ptit.btl16.client.network.ClientTransport) java.lang.reflect.Proxy.newProxyInstance(
                transportType.getClassLoader(), new Class<?>[]{transportType}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "isConnected" -> true;
                    case "getState" -> vn.ptit.btl16.client.network.ConnectionState.CONNECTED;
                    case "getEndpoint" -> "ws://127.0.0.1:1/ws";
                    case "connect" -> CompletableFuture.completedFuture(null);
                    case "ping" -> CompletableFuture.completedFuture(vn.ptit.btl16.common.protocol.WireMessage.response(
                            vn.ptit.btl16.common.protocol.MessageType.PONG, null, 0, Map.of("success", "true")));
                    case "sendRequest" -> {
                        var type = (vn.ptit.btl16.common.protocol.MessageType) arguments[0]; calls.add(type);
                        yield CompletableFuture.completedFuture(vn.ptit.btl16.common.protocol.WireMessage.response(type, null, 0,
                                Map.of("success", "true", "count", "0", "balance", "0", "available", "0")));
                    }
                    default -> null;
                });
        Stage stage = fx(Stage::new);
        var controller = fx(() -> {
            var result = new vn.ptit.btl16.client.fx.FxClientController(stage,
                    vn.ptit.btl16.common.config.ClientConfig.loadDefault(), new vn.ptit.btl16.client.model.ClientAppModel(), transport,
                    new vn.ptit.btl16.client.service.AccountApi(transport), new vn.ptit.btl16.client.service.AuctionApi(transport));
            result.start(); return result;
        });
        try {
            controllerErrors(controller, stage);
            for (String methodName : List.of("register", "showChangePassword", "roomPassword", "showActivityLog")) {
                CompletableFuture<Void> completed = new CompletableFuture<>();
                Platform.runLater(() -> {
                    try {
                        var method = controller.getClass().getDeclaredMethod(methodName); method.setAccessible(true); method.invoke(controller); completed.complete(null);
                    } catch (Throwable failure) { completed.completeExceptionally(failure); }
                });
                DialogPane pane = null;
                for (int attempt = 0; attempt < 100 && pane == null; attempt++) {
                    pane = fx(() -> Window.getWindows().stream().filter(window -> window instanceof Stage child && child.getOwner() == stage)
                            .map(window -> window.getScene().getRoot()).filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst().orElse(null));
                    if (pane == null) { Thread.sleep(30); }
                }
                TestSupport.check(pane != null, "controller opens " + methodName);
                DialogPane shown = pane; settle(); fx(() -> { captureScene(shown.getScene(), "client-" + methodName); return null; });
                fx(() -> { Stage window = (Stage) shown.getScene().getWindow(); window.setWidth(440); window.setHeight(440); return null; });
                settle(); fx(() -> {
                    captureScene(shown.getScene(), "client-" + methodName + "-compact");
                    var cancel = shown.getButtonTypes().stream().filter(type -> type.getButtonData().isCancelButton()).findFirst().orElseThrow();
                    Node button = shown.lookupButton(cancel); Bounds bounds = button.localToScene(button.getBoundsInLocal());
                    TestSupport.check(bounds.getMaxY() <= shown.getScene().getHeight() + 1, "controller cancel remains reachable");
                    ((Button) button).fire(); return null;
                });
                completed.get(10, TimeUnit.SECONDS);
            }
            TestSupport.check(calls.stream().noneMatch(type -> type.name().contains("REGISTER") || type.name().contains("PASSWORD")), "cancelling forms never sends an account mutation");
        } finally { fx(() -> { controller.close(); stage.close(); return null; }); }
    }

    private static void controllerErrors(vn.ptit.btl16.client.fx.FxClientController controller, Stage owner) throws Exception {
        String depositMessage = "Phòng hẹn giờ chỉ cho phép người đã đặt cọc đủ trước giờ bắt đầu tham gia.";
        for (int index = 0; index < 3; index++) {
            var response = index == 2 ? null : new vn.ptit.btl16.client.service.ApiResponse(
                    vn.ptit.btl16.common.protocol.WireMessage.response(vn.ptit.btl16.common.protocol.MessageType.ERROR,
                            null, 0, Map.of("success", "false", "errorCode", "ROOM_DEPOSIT_REQUIRED", "message", index == 0 ? depositMessage : "")));
            String expected = switch (index) {
                case 0 -> depositMessage;
                case 1 -> "Bạn cần đặt cọc đủ trước giờ bắt đầu để tham gia phòng này.";
                default -> "Máy chủ chưa phản hồi. Vui lòng thử lại.";
            };
            CompletableFuture<Object> completed = new CompletableFuture<>();
            Platform.runLater(() -> {
                try {
                    var method = controller.getClass().getDeclaredMethod("success", vn.ptit.btl16.client.service.ApiResponse.class, Throwable.class);
                    method.setAccessible(true); completed.complete(method.invoke(controller, response, null));
                } catch (Throwable failure) { completed.completeExceptionally(failure); }
            });
            DialogPane pane = null;
            for (int attempt = 0; attempt < 100 && pane == null; attempt++) {
                pane = fx(() -> Window.getWindows().stream().filter(window -> window instanceof Stage child && child.getOwner() == owner)
                        .map(window -> window.getScene().getRoot()).filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst().orElse(null));
                if (pane == null) { Thread.sleep(30); }
            }
            TestSupport.check(pane != null, "failed operation opens a message");
            DialogPane shown = pane; settle();
            int caseIndex = index;
            fx(() -> {
                TestSupport.equals("Lỗi", ((Stage) shown.getScene().getWindow()).getTitle(), "error window title is Vietnamese");
                TestSupport.equals(expected, shown.getContentText(), "error shows only a readable message without its protocol code");
                TestSupport.check(shown.getHeader() == null && shown.getGraphic() == null, "error does not add a decorative heading or icon");
                TestSupport.check(shown.lookup(".dialog-title") == null && shown.lookup(".dialog-eyebrow") == null, "error has no large or uppercase caption");
                var body = (ScrollPane) shown.getContent();
                TestSupport.check(body.getViewportBounds().getHeight() + 1 >= body.getContent().getLayoutBounds().getHeight(),
                        "short Vietnamese error is fully visible without scrolling");
                captureScene(shown.getScene(), "client-error-" + caseIndex);
                ((Button) shown.lookupButton(ButtonType.OK)).fire(); return null;
            });
            TestSupport.equals(false, completed.get(10, TimeUnit.SECONDS), "failed operation remains unsuccessful after dismissal");
        }
    }

    private static void capture(Dialog<?> dialog, String name, double width, double height) throws Exception {
        settle(); fx(() -> { Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow(); stage.setWidth(width); if (height > 0) { stage.setHeight(height); } return null; });
        settle(); fx(() -> {
            var pane = dialog.getDialogPane(); pane.applyCss(); pane.layout(); Scene scene = pane.getScene();
            for (Node marker : pane.lookupAll(".deposit-step")) {
                TestSupport.check(marker.getLayoutBounds().getWidth() >= 20, "deposit policy markers do not collapse at compact widths");
            }
            for (ButtonType type : pane.getButtonTypes()) {
                Node action = pane.lookupButton(type); if (!action.isVisible() || !action.isManaged()) { continue; }
                Bounds bounds = action.localToScene(action.getBoundsInLocal());
                TestSupport.check(bounds.getMinY() >= 0 && bounds.getMaxY() <= scene.getHeight() + 1, name + " keeps footer actions inside height: " + bounds);
                TestSupport.check(bounds.getMinX() >= 0 && bounds.getMaxX() <= scene.getWidth() + 1, name + " keeps footer actions inside width");
            }
            captureScene(scene, name);
            return null;
        });
    }

    private static void captureScene(Scene scene, String name) throws Exception {
        scene.getRoot().applyCss(); scene.getRoot().layout(); image(scene.snapshot(null), OUTPUT.resolve(name + ".png"));
        List<Map<String, Object>> nodes = new ArrayList<>(); describe(scene.getRoot(), nodes);
        SCREENS.add(Map.of("name", name, "width", scene.getWidth(), "height", scene.getHeight(), "nodes", nodes));
        if (scene.getRoot() instanceof DialogPane pane) {
            if (pane.getHeader() != null) { image(pane.getHeader().snapshot(null, null), OUTPUT.resolve(name + "--header.png")); }
            if (pane.getContent() != null) { image(pane.getContent().snapshot(null, null), OUTPUT.resolve(name + "--body.png")); }
            Node footer = pane.lookup(".button-bar"); if (footer != null) { image(footer.snapshot(null, null), OUTPUT.resolve(name + "--actions.png")); }
        }
    }

    private static void describe(Node node, List<Map<String, Object>> nodes) {
        if (!node.isVisible()) { return; }
        if (node instanceof Control control) {
            Bounds bounds = node.localToScene(node.getLayoutBounds()); Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", node.getClass().getSimpleName()); item.put("id", node.getId()); item.put("style", new ArrayList<>(node.getStyleClass()));
            if (control instanceof Labeled labeled) { item.put("text", labeled.getText()); item.put("font", labeled.getFont().getName()); }
            item.put("x", bounds.getMinX()); item.put("y", bounds.getMinY()); item.put("width", bounds.getWidth()); item.put("height", bounds.getHeight()); nodes.add(item);
        }
        if (node instanceof Parent parent) { for (Node child : parent.getChildrenUnmodifiable()) { describe(child, nodes); } }
    }

    private static void image(WritableImage image, Path file) throws Exception {
        int width = (int) image.getWidth(), height = (int) image.getHeight(); int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage bitmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB); bitmap.setRGB(0, 0, width, height, pixels, 0, width);
        ImageIO.write(bitmap, "png", file.toFile());
    }
    private static void settle() throws Exception { fx(() -> null); Thread.sleep(160); fx(() -> null); }
    private static <T> T fx(Callable<T> action) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> { try { future.complete(action.call()); } catch (Throwable failure) { future.completeExceptionally(failure); } }); return future.get(20, TimeUnit.SECONDS);
    }
}
