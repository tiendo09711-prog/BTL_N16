package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import vn.ptit.btl16.client.fx.ProductMediaEditor;
import vn.ptit.btl16.client.model.ClientWireParser;
import vn.ptit.btl16.client.service.ProductMediaApi;
import vn.ptit.btl16.server.auction.repository.TestAuctionRepository;
import vn.ptit.btl16.server.auction.service.AuctionWireData;
import vn.ptit.btl16.server.media.MediaHttpServer;

import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Image-only editor lifecycle; independent of the machine's video decoder. */
public final class ProductCodesUiSelfTest {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        var repository = new TestAuctionRepository();
        var fixture = new AuctionRulesSelfTest.Fixture(repository);
        var media = fixture.management.media();
        Path photo = Files.createTempFile("btl16-product-code-", ".png");
        Files.write(photo, TestMedia.png());
        AtomicInteger saves = new AtomicInteger();
        try (var server = new MediaHttpServer(media, "127.0.0.1")) {
            server.start();
            var api = new ProductMediaApi(URI.create("http://127.0.0.1:" + media.port()), fixture.host::getSessionToken);
            var editor = fx(() -> { var value = new ProductMediaEditor(null, api, null, saves::incrementAndGet); value.show(); return value; });
            waitFor(() -> field(editor, "draft") != null, "new editor draft");
            fx(() -> {
                var code = (TextField) field(editor, "code");
                TestSupport.check(!code.isEditable() && code.getText().isEmpty(), "new product code is read-only and requires no input");
                ((TextField) field(editor, "name")).setText("Sản phẩm thử mã tự động");
                ((TextArea) field(editor, "description")).setText(TestMedia.DESCRIPTION);
                var add = ProductMediaEditor.class.getDeclaredMethod("addFiles", List.class, String.class, boolean.class);
                add.setAccessible(true); add.invoke(editor, List.of(photo.toFile()), "IMAGE", false);
                return null;
            });
            waitFor(() -> !((Button) field(editor, "save")).isDisabled(), "valid image product saves with an empty code field");
            fx(() -> { ((Button) field(editor, "save")).fire(); return null; });
            waitFor(() -> !((Stage) field(editor, "stage")).isShowing(), "new product committed");
            var saved = repository.findProductsByOwner(1).get(0);
            TestSupport.equals("MH1", saved.getCode(), "editor save receives MH1");
            TestSupport.equals(1, saves.get(), "successful create refreshes the product list");

            var product = ClientWireParser.product(AuctionWireData.product(saved, Instant.now()), "");
            var editing = fx(() -> { var value = new ProductMediaEditor(null, api, product, saves::incrementAndGet); value.show(); return value; });
            waitFor(() -> field(editing, "draft") != null && !((boolean) field(editing, "busy"))
                    && !((Button) field(editing, "save")).isDisabled(), "existing media loaded for editing");
            fx(() -> {
                var code = (TextField) field(editing, "code");
                TestSupport.check(!code.isEditable(), "existing product code cannot be edited");
                TestSupport.equals("MH1", code.getText(), "existing code is displayed");
                ((TextField) field(editing, "name")).setText("Sản phẩm đã sửa tên");
                ((Button) field(editing, "save")).fire(); return null;
            });
            waitFor(() -> !((Stage) field(editing, "stage")).isShowing(), "edited product committed");
            var updated = repository.findProductById(saved.getProductId()).orElseThrow();
            TestSupport.equals("MH1", updated.getCode(), "editing preserves the assigned code");
            TestSupport.equals("Sản phẩm đã sửa tên", updated.getName(), "editing still saves product content");
            TestSupport.equals(2, saves.get(), "successful edit refreshes the product list");
            System.out.println("[PASS] ProductCodesUiSelfTest: read-only automatic codes, real image upload, create/save and edit/save");
        } finally {
            fx(() -> { new ArrayList<>(javafx.stage.Window.getWindows()).forEach(window -> window.hide()); return null; });
            media.close(); Files.deleteIfExists(photo); Platform.exit();
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } });
        return result.get(15, TimeUnit.SECONDS);
    }
    private static void waitFor(Callable<Boolean> condition, String description) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < end) { if (fx(condition)) { return; } Thread.sleep(50); }
        throw new AssertionError("Timeout: " + description);
    }
}
