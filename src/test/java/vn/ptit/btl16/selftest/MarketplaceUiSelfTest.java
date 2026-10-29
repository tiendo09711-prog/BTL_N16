package vn.ptit.btl16.selftest;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import vn.ptit.btl16.client.fx.AuctionMarketplacePane;
import vn.ptit.btl16.client.model.ClientAuction;
import vn.ptit.btl16.client.service.ProductMediaApi;
import vn.ptit.btl16.common.fx.DesktopTheme;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class MarketplaceUiSelfTest {
    public static void main(String[] args) throws Exception {
        var json = new ObjectMapper();
        var manifest = json.readTree("""
                {"media":[{"kind":"VIDEO","thumbnail":"video.jpg"},
                {"kind":"IMAGE","thumbnail":"first.jpg"},{"kind":"IMAGE","thumbnail":"second.jpg"}]}
                """);
        TestSupport.equals("first.jpg", ProductMediaApi.firstImage(manifest).path("thumbnail").asText(), "cover uses the first uploaded image, skipping video");
        TestSupport.check(ProductMediaApi.firstImage(json.readTree("{\"media\":[]}")) == null, "missing image has no invented cover");
        Platform.startup(() -> Platform.setImplicitExit(false));
        var loads = new AtomicInteger();
        var enters = new AtomicInteger();
        var delayed = new CompletableFuture<Image>();
        var first = room(1, "Bình gốm cổ", "2500000", 1);
        var second = room(2, "Đồng hồ cổ", "4200000", 1);
        Stage stage = fx(Stage::new);
        AuctionMarketplacePane gallery = fx(() -> {
            var pane = new AuctionMarketplacePane();
            pane.onEnter(auction -> enters.incrementAndGet());
            pane.configure(auction -> { loads.incrementAndGet(); return auction.getAuctionId() == 3 ? delayed
                    : auction.getAuctionId() == 4 ? CompletableFuture.failedFuture(new IllegalStateException("Unavailable"))
                    : CompletableFuture.completedFuture(new Image(new ByteArrayInputStream(TestMedia.png()))); }, Instant::now);
            pane.getItems().setAll(first, second);
            var scene = new Scene(new StackPane(pane), 980, 620); DesktopTheme.apply(scene); stage.setScene(scene); stage.show();
            return pane;
        });
        try {
            drain();
            fx(() -> {
                gallery.applyCss(); gallery.layout();
                Button one = (Button) gallery.lookup("#auction-card-1"), two = (Button) gallery.lookup("#auction-card-2");
                TestSupport.check(two.localToScene(two.getBoundsInLocal()).getMinX() > one.localToScene(one.getBoundsInLocal()).getMinX() + 300,
                        "desktop displays large product photos side by side");
                ImageView cover = (ImageView) one.lookup("ImageView");
                TestSupport.check(cover.getImage() != null && cover.getBoundsInParent().getWidth() > 300 && cover.getBoundsInParent().getHeight() > 160,
                        "card cover fills the product display area");
                TestSupport.check(gallery.getSelectionModel().getSelectedItem() == null, "browsing never automatically enters or selects a room");
                one.fire(); TestSupport.equals(1L, gallery.getSelectionModel().getSelectedItem().getAuctionId(), "clicking a product selects its auction");
                gallery.refresh(); gallery.refresh(); TestSupport.equals(2, loads.get(), "countdown updates reuse loaded covers");
                gallery.getItems().setAll(room(1, "Bình gốm cổ", "2600000", 1), second);
                TestSupport.equals(1L, gallery.getSelectionModel().getSelectedItem().getAuctionId(), "live price update keeps product selection");
                TestSupport.equals("2.600.000 ₫", ((Label) gallery.lookup("#auction-card-1 .product-card-price")).getText(), "card displays the updated auction price");
                TestSupport.equals(2, loads.get(), "price updates do not download covers again");
                ((javafx.scene.control.ToggleButton) two.lookup(".market-favorite")).fire();
                TestSupport.check(gallery.isFavorite(2), "favorite control marks its own product");
                TestSupport.equals(1L, gallery.getSelectionModel().getSelectedItem().getAuctionId(), "favorite action does not switch the preview or enter another room");
                ((Button) gallery.lookup("#auction-enter-2")).fire();
                TestSupport.equals(2L, gallery.getSelectionModel().getSelectedItem().getAuctionId(), "room shortcut first selects the matching product");
                TestSupport.equals(1, enters.get(), "room shortcut invokes the real join action exactly once");
                gallery.setListMode(true); gallery.setListMode(false);
                TestSupport.equals(2, loads.get(), "changing the display mode keeps uploaded cover images");
                return null;
            });
            screenshot(stage, "target/marketplace-grid-preview.png");
            fx(() -> { stage.setWidth(500); return null; });
            TestSupport.waitUntil(() -> {
                try { return fx(() -> gallery.getViewportBounds().getWidth() < 560
                        && ((GridPane) gallery.lookup("#auction-product-grid")).getColumnConstraints().size() == 1); }
                catch (Exception error) { throw new IllegalStateException(error); }
            }, 5000, "gallery responds to the native window resize");
            fx(() -> {
                stage.getScene().getRoot().layout();
                var grid = (GridPane) gallery.lookup("#auction-product-grid");
                TestSupport.equals(1, grid.getColumnConstraints().size(), "narrow gallery adapts to one column");
                gallery.getItems().setAll(room(3, "Ảnh đang tải", "100000", 1));
                gallery.getItems().setAll(room(4, "Sản phẩm vẫn mở được", "200000", 1)); return null;
            });
            delayed.complete(new Image(new ByteArrayInputStream(TestMedia.png()))); drain();
            fx(() -> {
                TestSupport.check(gallery.lookup("#auction-card-3") == null, "late cover never restores a filtered-out product");
                Button unavailable = (Button) gallery.lookup("#auction-card-4"); unavailable.fire();
                TestSupport.equals(4L, gallery.getSelectionModel().getSelectedItem().getAuctionId(), "image failure does not prevent opening the product");
                gallery.getItems().clear();
                TestSupport.check(gallery.lookup("#auction-marketplace-empty") != null && gallery.getSelectionModel().getSelectedItem() == null,
                        "empty results clear the old room and display a helpful state"); return null;
            });
            fx(() -> { stage.setWidth(1320); gallery.setFeatured(featured(6)); return null; }); drain();
            TestSupport.waitUntil(() -> { try { return fx(() -> ((GridPane) gallery.lookup("#featured-auctions-grid")).getColumnConstraints().size() == 5); } catch (Exception e) { throw new RuntimeException(e); } }, 5000, "featured strip expands to five columns");
            int loaded = loads.get();
            fx(() -> {
                TestSupport.equals(5, gallery.lookupAll(".featured-card").size(), "featured area contains at most five rooms");
                var one = gallery.lookup("#featured-auction-10"); var two = gallery.lookup("#featured-auction-11");
                TestSupport.check(one.getBoundsInParent().getHeight() < 400, "featured cards remain compact");
                TestSupport.check(Math.abs(one.getLayoutY() - two.getLayoutY()) < 1, "featured cards align at the top of the row independently of decorative shadows");
                ((Button) gallery.lookup("#featured-auction-12")).fire();
                TestSupport.equals(12L, gallery.getSelectionModel().getSelectedItem().getAuctionId(), "featured room can be selected outside the filtered catalog");
                TestSupport.equals(2, enters.get(), "featured click calls the existing enter-room action once");
                gallery.setFeatured(featured(5)); gallery.refresh(); return null;
            }); drain();
            TestSupport.equals(loaded, loads.get(), "featured score refresh reuses product covers");
            screenshot(stage, "target/marketplace-featured.png");
            fx(() -> { stage.setWidth(500); return null; });
            TestSupport.waitUntil(() -> { try { return fx(() -> ((GridPane) gallery.lookup("#featured-auctions-grid")).getColumnConstraints().size() == 2); } catch (Exception e) { throw new RuntimeException(e); } }, 5000, "featured rooms adapt to a narrow viewport");
            screenshot(stage, "target/marketplace-featured-narrow.png");
            fx(() -> {
                var data = featured(2); data.put("auction.0.endTime", Instant.now().minusSeconds(1).toString()); data.put("auction.1.featuredScore", "999");
                gallery.setFeatured(data); gallery.applyCss(); gallery.layout();
                TestSupport.check(gallery.lookup("#featured-auction-10") == null, "expired recommendations disappear immediately");
                TestSupport.equals("999 điểm nổi bật", ((Label) gallery.lookup("#featured-auction-11 .featured-points")).getText(), "removing an expired room keeps the next room's own score");
                gallery.clearFeatured(); TestSupport.check(gallery.lookupAll(".featured-card").isEmpty(), "logout clearing removes featured rooms"); return null;
            });
            System.out.println("[PASS] MarketplaceUiSelfTest: first photo, responsive grid, product clicks, live prices, cover reuse, stale images and empty/error states");
        } finally { fx(() -> { gallery.close(); stage.close(); return null; }); Platform.exit(); }
    }

    private static ClientAuction room(long id, String name, String price, long imageVersion) {
        var now = Instant.now();
        return new ClientAuction(id, id, "CODE", name, "Sản phẩm trưng bày trong sàn đấu giá.", 1, "host", BigDecimal.ONE,
                BigDecimal.ONE, new BigDecimal(price), null, "", now.minusSeconds(30), now.plusSeconds(600), "OPEN", null,
                1, 0, "PUBLIC", false, true, "image/jpeg", imageVersion);
    }
    private static java.util.Map<String,String> featured(int count) {
        var data = new java.util.LinkedHashMap<String,String>(); data.put("count", count + "");
        String[] names = {"Bình gốm men ngọc", "Đồng hồ cổ điển", "Tranh sơn dầu", "Máy ảnh phim", "Tượng đồng trưng bày", "Phiên ngoài Top 5"};
        for (int i = 0; i < count; i++) {
            String p = "auction." + i + ".";
            data.putAll(java.util.Map.of(p + "auctionId", (10 + i) + "", p + "productId", (10 + i) + "", p + "productName", names[i], p + "startTime", Instant.now().minusSeconds(60).toString(), p + "endTime", Instant.now().plusSeconds(600).toString(), p + "status", "OPEN", p + "currentPrice", "2500000", p + "hasImage", "true", p + "imageVersion", "1", p + "watcherCount", (30 - i) + ""));
            data.putAll(java.util.Map.of(p + "featuredScore", (100 - i * 10) + "", p + "totalBids", "20", p + "movementCount", "50"));
        } return data;
    }
    private static void screenshot(Stage stage, String path) throws Exception {
        fx(() -> {
            var image = stage.getScene().snapshot(null);
            var bitmap = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < bitmap.getHeight(); y++) { for (int x = 0; x < bitmap.getWidth(); x++) { bitmap.setRGB(x, y, image.getPixelReader().getArgb(x, y)); } }
            javax.imageio.ImageIO.write(bitmap, "png", new java.io.File(path)); return null;
        });
    }
    private static void drain() throws Exception { fx(() -> null); fx(() -> null); }
    private static <T> T fx(Callable<T> action) throws Exception {
        var future = new CompletableFuture<T>(); Platform.runLater(() -> { try { future.complete(action.call()); } catch (Throwable error) { future.completeExceptionally(error); } });
        return future.get(15, TimeUnit.SECONDS);
    }
}
