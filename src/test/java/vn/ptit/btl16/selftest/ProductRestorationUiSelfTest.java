package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.ptit.btl16.client.fx.FxClientController;
import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.model.ClientProduct;
import vn.ptit.btl16.client.network.NetworkClient;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.common.config.ClientConfig;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.ProductRestorationStatus;
import vn.ptit.btl16.common.protocol.WireMessage;
import vn.ptit.btl16.server.auction.model.Product;
import vn.ptit.btl16.server.auction.service.AuctionWireData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ProductRestorationUiSelfTest {
    private ProductRestorationUiSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        StubTransport transport = new StubTransport();
        Stage owner = fx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(), 1, 1));
            return stage;
        });
        FxClientController controller = fx(() -> new FxClientController(owner, ClientConfig.loadDefault(),
                new ClientAppModel(), transport, new AccountApi(transport), new AuctionApi(transport)));
        try {
            Stage window = fx(() -> {
                var method = FxClientController.class.getDeclaredMethod("showProducts");
                method.setAccessible(true);
                method.invoke(controller);
                return (Stage) Window.getWindows().stream().filter(Window::isShowing).findFirst().orElseThrow();
            });
            @SuppressWarnings("unchecked")
            TableView<ClientProduct> table = fx(() -> (TableView<ClientProduct>) window.getScene().lookup("#seller-products"));
            Button restore = fx(() -> (Button) window.getScene().lookup("#restore-product"));
            Label hint = fx(() -> (Label) window.getScene().lookup("#product-restoration-status"));
            waitFor(() -> table.getItems().size() == 5, "seller list loads through the API stub");
            fx(() -> {
                table.getSelectionModel().select(0);
                TestSupport.check(restore.isDisabled(), "active item needs no restoration");
                table.getSelectionModel().select(2);
                TestSupport.check(restore.isDisabled(), "sold item disables restoration");
                TestSupport.check(hint.getText().contains("ng\u01b0\u1eddi th\u1eafng")
                        && hint.getText().contains("ch\u01b0a thanh to\u00e1n"), "sold rejection explains unpaid items too");
                restore.fire();
                TestSupport.equals(0, transport.restoreRequests, "disabled restore sends no request");
                table.getSelectionModel().select(3);
                TestSupport.check(restore.isDisabled() && hint.getText().contains("phi\u00ean \u0111ang m\u1edf"), "open auction rejection is explained");
                table.getSelectionModel().select(1);
                TestSupport.check(!restore.isDisabled(), "eligible item enables restoration");
                return null;
            });
            snapshot(window, "seller-products-before.png");
            fx(() -> {
                CompletableFuture<Void> confirmation = dismissLater(ButtonType.YES, "");
                restore.fire();
                confirmation.join();
                TestSupport.equals(1, transport.restoreRequests, "one click sends exactly one restore request");
                TestSupport.check(restore.isDisabled(), "pending request disables duplicate clicks");
                restore.fire();
                TestSupport.equals(1, transport.restoreRequests, "pending restore cannot be submitted twice");
                return null;
            });
            transport.complete(true);
            dismissLater(ButtonType.OK, "").get(12, TimeUnit.SECONDS);
            waitFor(() -> table.getSelectionModel().getSelectedItem() != null
                    && table.getSelectionModel().getSelectedItem().getProductId() == 2L
                    && table.getSelectionModel().getSelectedItem().isActive(), "successful restore refreshes the selected product");
            fx(() -> {
                TestSupport.check(restore.isDisabled(), "restored item no longer offers restore");
                table.getSelectionModel().select(4);
                TestSupport.check(!restore.isDisabled(), "stale eligible item initially offers restore");
                CompletableFuture<Void> confirmation = dismissLater(ButtonType.YES, "");
                restore.fire();
                confirmation.join();
                return null;
            });
            transport.complete(false);
            dismissLater(ButtonType.OK, "ng\u01b0\u1eddi th\u1eafng").get(12, TimeUnit.SECONDS);
            waitFor(() -> table.getSelectionModel().getSelectedItem() != null
                    && table.getSelectionModel().getSelectedItem().getRestorationStatus() == ProductRestorationStatus.SOLD,
                    "server refusal refreshes stale eligibility");
            fx(() -> {
                TestSupport.check(restore.isDisabled(), "rejected sold product is disabled after refresh");
                return null;
            });
            snapshot(window, "seller-products-after.png");
            System.out.println("[PASS] ProductRestorationUiSelfTest: JavaFX eligibility/reasons, confirmation, single pending request, success and stale rejection refresh (stub only)");
        } finally {
            fx(() -> {
                controller.close();
                for (Window window : List.copyOf(Window.getWindows())) {
                    window.hide();
                }
                owner.hide();
                return null;
            });
            Platform.exit();
        }
    }

    private static CompletableFuture<Void> dismissLater(ButtonType button, String expectedText) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                dismiss(button, expectedText);
                result.complete(null);
            } catch (Throwable exception) {
                result.completeExceptionally(exception);
            }
        });
        return result;
    }

    private static void dismiss(ButtonType button, String expectedText) {
        for (Window window : List.copyOf(Window.getWindows())) {
            if (window.getScene() != null && window.getScene().getRoot() instanceof DialogPane pane
                    && pane.getButtonTypes().contains(button)) {
                boolean matches = pane.getContentText().contains(expectedText);
                ((Button) pane.lookupButton(button)).fire();
                TestSupport.check(matches, "dialog explains the restoration outcome");
                return;
            }
        }
        throw new AssertionError("Expected test dialog did not appear");
    }

    private static void waitFor(Callable<Boolean> condition, String message) throws Exception {
        TestSupport.waitUntil(() -> {
            try {
                return fx(condition);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }, 10000, message);
    }

    private static <Result> Result fx(Callable<Result> action) throws Exception {
        CompletableFuture<Result> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.call());
            } catch (Throwable exception) {
                result.completeExceptionally(exception);
            }
        });
        return result.get(15, TimeUnit.SECONDS);
    }

    private static void snapshot(Stage window, String filename) throws Exception {
        Path output = Path.of("target", "restore-ui-preview", filename);
        Files.createDirectories(output.getParent());
        fx(() -> {
            window.getScene().getRoot().applyCss();
            window.getScene().getRoot().layout();
            var image = window.getScene().snapshot(null);
            BufferedImage bitmap = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int row = 0; row < bitmap.getHeight(); row++) {
                for (int column = 0; column < bitmap.getWidth(); column++) {
                    bitmap.setRGB(column, row, image.getPixelReader().getArgb(column, row));
                }
            }
            ImageIO.write(bitmap, "png", output.toFile());
            return null;
        });
    }

    private static final class StubTransport extends NetworkClient {
        private int restoreRequests;
        private boolean restored;
        private boolean sold;
        private CompletableFuture<WireMessage> pending;

        private StubTransport() {
            super(2_097_152, 1000, 10000);
        }

        @Override
        public CompletableFuture<WireMessage> sendRequest(MessageType type, Map<String, String> data) {
            if (type == MessageType.RESTORE_PRODUCT) {
                restoreRequests++;
                pending = new CompletableFuture<>();
                return pending;
            }
            TestSupport.equals(MessageType.MY_PRODUCTS, type, "UI test never opens a real connection or production server");
            List<Product> products = new ArrayList<>();
            for (int index = 1; index <= 5; index++) {
                products.add(new Product(index, 1L, "host", "ITEM_" + index, "Item " + index, "Original description",
                        index == 1 || (index == 2 && restored), Instant.EPOCH, Instant.EPOCH));
            }
            Map<String, String> response = AuctionWireData.productList(products, Instant.EPOCH, product ->
                    product.isActive() ? ProductRestorationStatus.ACTIVE : switch ((int) product.getProductId()) {
                        case 3 -> ProductRestorationStatus.SOLD;
                        case 4 -> ProductRestorationStatus.OPEN_AUCTION;
                        case 5 -> sold ? ProductRestorationStatus.SOLD : ProductRestorationStatus.ELIGIBLE;
                        default -> ProductRestorationStatus.ELIGIBLE;
                    });
            response.put("success", "true");
            return CompletableFuture.completedFuture(WireMessage.response(MessageType.MY_PRODUCTS_RESULT, null, 1L, response));
        }

        private void complete(boolean success) {
            restored = restored || success;
            sold = sold || !success;
            pending.complete(WireMessage.response(success ? MessageType.RESTORE_PRODUCT_RESULT : MessageType.ERROR, null, 2L,
                    success ? Map.of("success", "true", "active", "true")
                            : Map.of("success", "false", "errorCode", "PRODUCT_ALREADY_SOLD", "message", "Already sold")));
        }
    }
}
