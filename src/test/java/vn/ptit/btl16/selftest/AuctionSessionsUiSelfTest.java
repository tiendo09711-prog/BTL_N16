package vn.ptit.btl16.selftest;

import javafx.animation.Animation;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;
import vn.ptit.btl16.server.fx.AuctionSessionsPage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class AuctionSessionsUiSelfTest {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        var worker = Executors.newSingleThreadExecutor();
        Stage stage = null;
        try (var fixture = new AuctionSessionsSelfTest.Fixture(3, 4)) {
            Stage window = fx(Stage::new); stage = window;
            var page = fx(() -> {
                var content = new AuctionSessionsPage(window, () -> fixture.server, worker);
                Scene scene = new Scene(content, 1060, 660); DesktopTheme.apply(scene);
                scene.getStylesheets().add(AuctionSessionsUiSelfTest.class.getResource("/ui/server-admin.css").toExternalForm());
                window.setScene(scene); window.show(); content.activate(); return content;
            });
            waitFor(() -> !loading(page), "session list loads");
            fx(() -> {
                Node roomFilter = window.getScene().lookup("#server-session-room-filter");
                double bottom = roomFilter.localToScene(roomFilter.getLayoutBounds()).getMaxY();
                for (String id : new String[]{"server-session-from","server-session-to","server-session-type","server-session-filter","server-session-reset"}) {
                    Node control = window.getScene().lookup("#" + id);
                    TestSupport.check(Math.abs(bottom-control.localToScene(control.getLayoutBounds()).getMaxY())<=1,"filter inputs and actions share the same bottom edge: " + id);
                    TestSupport.equals(40.0,control.getLayoutBounds().getHeight(),"filter controls share one consistent height: " + id);
                }
                return null;
            });
            TableView<AuctionSnapshot> table = fx(() -> {
                TestSupport.check(((ToggleButton) window.getScene().lookup("#server-session-card-mode")).isSelected(), "client-style photo cards are the default view");
                ((ToggleButton) window.getScene().lookup("#server-session-list-mode")).fire();
                window.getScene().getRoot().applyCss(); window.getScene().getRoot().layout();
                @SuppressWarnings("unchecked") var value = (TableView<AuctionSnapshot>) window.getScene().lookup("#server-session-list");
                TestSupport.equals(4, value.getItems().size(), "list includes old ended rooms"); return value;
            });
            snapshot(window, "list");
            fx(() -> {
                ((ToggleButton) window.getScene().lookup("#server-session-card-mode")).fire();
                window.getScene().getRoot().applyCss(); window.getScene().getRoot().layout();
                TestSupport.check(window.getScene().lookup("#server-session-cards") != null, "card browsing is available");
                TilePane cards = (TilePane) window.getScene().lookup("#server-session-cards");
                TestSupport.equals(cards.getChildren().getFirst().getLayoutY(), cards.getChildren().get(2).getLayoutY(), "wide layout fits three photo cards on the same row");
                return null;
            });
            snapshot(window, "cards");
            fx(() -> {
                ((ToggleButton) window.getScene().lookup("#server-session-list-mode")).fire();
                ((TextField) window.getScene().lookup("#server-session-room-filter")).setText(fixture.open.getRoomCode());
                ((Button) window.getScene().lookup("#server-session-filter")).fire(); return null;
            });
            waitFor(() -> !loading(page), "room code filter completes");
            fx(() -> { TestSupport.equals(1, table.getItems().size(), "code identifies a single room"); return null; });
            open(table, fixture.open.getAuctionId());
            waitFor(() -> !loading(page), "product details load");
            snapshot(window, "product");
            fx(() -> {
                TabPane tabs = (TabPane) window.getScene().lookup("#server-session-tabs");
                TestSupport.equals(3, tabs.getTabs().size(), "three requested detail tabs");
                TestSupport.check(window.getScene().lookup("#server-session-tab-buttons") != null, "detail navigation uses the same rounded segmented controls as the client");
                String text = labels(tabs.getTabs().getFirst().getContent());
                TestSupport.check(text.contains("ADMIN_OPEN") && text.contains("Giá khởi điểm") && text.contains("Người dẫn đầu"), "product details include content, prices and leader");
                TestSupport.check(((ImageView) window.getScene().lookup("#server-session-product-image")).getImage() != null, "product gallery shows actual media");
                tabs.getSelectionModel().select(1); return null;
            });
            snapshot(window, "seller");
            responsiveDetails(window);
            fx(() -> {
                TabPane tabs = (TabPane) window.getScene().lookup("#server-session-tabs");
                String seller = labels(tabs.getTabs().get(1).getContent());
                TestSupport.check(seller.contains("demo@local.test") && seller.contains("Số điện thoại") && !seller.toLowerCase().contains("password"), "seller profile excludes credentials");
                tabs.getSelectionModel().select(2); return null;
            });
            waitFor(() -> !((Label) window.getScene().lookup("#room-events-status")).getText().startsWith("Đang tải"), "shared client journal loads");
            fx(() -> {
                @SuppressWarnings("unchecked") var chart = (AreaChart<Number, Number>) window.getScene().lookup("#room-price-chart");
                TestSupport.equals(5, chart.getData().getFirst().getData().size(), "shared chart includes start and all four bids");
                VBox people = (VBox) window.getScene().lookup("#server-session-participants");
                TestSupport.equals(5, people.getChildren().size(), "all three historical attendees are shown");
                TestSupport.check(labels(people).contains("@alice") && labels(people).contains("Alice"), "participant row includes username and real name");
                return null;
            });
            snapshot(window, "history");
            fx(() -> {
                ScrollPane scroll = (ScrollPane) ((TabPane) window.getScene().lookup("#server-session-tabs")).getTabs().get(2).getContent();
                scroll.setVvalue(1); return null;
            });
            snapshot(window, "participants");
            fx(() -> {
                ((TabPane) window.getScene().lookup("#server-session-tabs")).getSelectionModel().select(0);
                return null;
            });
            fx(() -> {
                var tabs = (TabPane) window.getScene().lookup("#server-session-tabs");
                TestSupport.equals(0.0, ((ScrollPane) tabs.getTabs().getFirst().getContent()).getVvalue(), "switching tabs always starts at the top instead of clipping the image/profile");
                TestSupport.check(((ToggleButton) window.getScene().lookup("#server-session-tab-0")).isSelected(), "segmented selection stays in sync with tab content");
                return null;
            });
            fx(() -> { ((Button) window.getScene().lookup("#server-session-back")).fire(); return null; });
            waitFor(() -> !loading(page), "return to list");
            fx(() -> {
                TestSupport.equals(1, table.getItems().size(), "code filter survives detail navigation");
                ((Button) window.getScene().lookup("#server-session-reset")).fire(); return null;
            });
            waitFor(() -> !loading(page), "clear filters");
            open(table, fixture.anonymous.getAuctionId());
            waitFor(() -> !loading(page), "anonymous details load");
            fx(() -> { ((TabPane) window.getScene().lookup("#server-session-tabs")).getSelectionModel().select(2); return null; });
            waitFor(() -> !((Label) window.getScene().lookup("#room-events-status")).getText().startsWith("Đang tải"), "anonymous journal loads");
            fx(() -> {
                var tabs = (TabPane) window.getScene().lookup("#server-session-tabs");
                String history = labels(tabs.getTabs().get(2).getContent());
                TestSupport.check(!history.contains("alice") && !history.contains("Alice") && !history.contains("bob")
                        && history.contains("Khách #"), "anonymous history, chart and roster never reveal identity");
                return null;
            });
            snapshot(window, "anonymous-history");
            fx(() -> { ((Button) window.getScene().lookup("#server-session-back")).fire(); return null; });
            waitFor(() -> !loading(page), "back from anonymous room");
            open(table, fixture.ended.getAuctionId());
            waitFor(() -> !loading(page), "archived ended details load");
            fx(() -> {
                var tabs = (TabPane) window.getScene().lookup("#server-session-tabs");
                String text = labels(tabs.getTabs().getFirst().getContent());
                TestSupport.check(text.contains("Giá đã chốt") && text.contains("Người chiến thắng") && text.contains("Không có người chiến thắng"), "ended room uses final-price and winner labels without inventing a winner");
                ((Button) window.getScene().lookup("#server-session-back")).fire(); return null;
            });
            waitFor(() -> !loading(page), "back from ended room");
            fx(() -> {
                ((DatePicker) window.getScene().lookup("#server-session-from")).setValue(LocalDate.now());
                ((DatePicker) window.getScene().lookup("#server-session-to")).setValue(LocalDate.now().minusDays(1));
                ((Button) window.getScene().lookup("#server-session-filter")).fire();
                TestSupport.check(((Label) window.getScene().lookup("#server-sessions-status")).getText().contains("không hợp lệ"), "reversed date range has visible error");
                window.setWidth(660); window.setHeight(620); return null;
            });
            snapshot(window, "compact");
            fx(() -> {
                page.deactivate();
                TestSupport.equals(Animation.Status.STOPPED, ((Timeline) field(page, "poll")).getStatus(), "inactive page stops polling");
                return null;
            });
            System.out.println("[PASS] AuctionSessionsUiSelfTest: list/cards, filters, gallery, seller, shared chart/journal, complete roster, anonymity and archived rooms");
        } finally {
            Stage window = stage;
            if (window != null) { fx(() -> { window.hide(); return null; }); }
            worker.shutdownNow(); Platform.exit();
        }
    }

    private static void open(TableView<AuctionSnapshot> table, long room) throws Exception {
        fx(() -> {
            table.getSelectionModel().select(table.getItems().stream().filter(item -> item.getAuctionId() == room).findFirst().orElseThrow());
            table.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false)); return null;
        });
    }

    private static void responsiveDetails(Stage window) throws Exception {
        double width = fx(window::getWidth), height = fx(window::getHeight);
        checkColumns(window, "#server-session-seller-columns", false);
        fx(() -> {
            ((ToggleButton) window.getScene().lookup("#server-session-tab-0")).fire();
            return null;
        });
        checkColumns(window, "#server-session-product-columns", false);
        fx(() -> { window.setWidth(660); window.setHeight(620); return null; });
        snapshot(window, "product-compact");
        checkColumns(window, "#server-session-product-columns", true);
        fx(() -> {
            TabPane tabs = (TabPane) window.getScene().lookup("#server-session-tabs");
            ((ScrollPane) tabs.getSelectionModel().getSelectedItem().getContent()).setVvalue(.65);
            return null;
        });
        snapshot(window, "product-compact-info");
        fx(() -> {
            ((ToggleButton) window.getScene().lookup("#server-session-tab-1")).fire();
            return null;
        });
        snapshot(window, "seller-compact");
        checkColumns(window, "#server-session-seller-columns", true);
        fx(() -> {
            TabPane tabs = (TabPane) window.getScene().lookup("#server-session-tabs");
            ((ScrollPane) tabs.getSelectionModel().getSelectedItem().getContent()).setVvalue(.6);
            return null;
        });
        snapshot(window, "seller-compact-contact");
        fx(() -> {
            TabPane tabs = (TabPane) window.getScene().lookup("#server-session-tabs");
            ((ScrollPane) tabs.getSelectionModel().getSelectedItem().getContent()).setVvalue(0);
            window.setWidth(width); window.setHeight(height); return null;
        });
        waitFor(() -> window.getScene().getWidth() > 1000, "wide layout restored");
        checkColumns(window, "#server-session-seller-columns", false);
    }

    private static void checkColumns(Stage window, String selector, boolean stacked) throws Exception {
        fx(() -> {
            window.getScene().getRoot().applyCss(); window.getScene().getRoot().layout();
            GridPane columns = (GridPane) window.getScene().lookup(selector);
            Node first = columns.getChildren().getFirst(), second = columns.getChildren().get(1);
            TestSupport.equals(stacked ? 1 : 0, GridPane.getRowIndex(second), "detail cards stack only in compact windows: " + selector);
            var firstBounds = first.localToScene(first.getLayoutBounds());
            var secondBounds = second.localToScene(second.getLayoutBounds());
            TestSupport.check(stacked ? secondBounds.getMinY() >= firstBounds.getMaxY() : secondBounds.getMinX() >= firstBounds.getMaxX(), "detail cards never overlap: " + selector);
            var gridBounds = columns.localToScene(columns.getLayoutBounds());
            TestSupport.check(gridBounds.getMaxY() + 1 >= Math.max(firstBounds.getMaxY(), secondBounds.getMaxY()), "responsive grid reserves enough height for every card: " + selector);
            TestSupport.check(gridBounds.getMaxX() + 1 >= Math.max(firstBounds.getMaxX(), secondBounds.getMaxX()), "detail cards never extend beyond the responsive grid: " + selector);
            TabPane tabs = (TabPane) window.getScene().lookup("#server-session-tabs");
            ScrollPane scroll = (ScrollPane) tabs.getSelectionModel().getSelectedItem().getContent();
            TestSupport.check(columns.getWidth() <= scroll.getViewportBounds().getWidth() + 1, "detail content fits the viewport without horizontal clipping: " + selector);
            TestSupport.check(scroll.lookupAll(".scroll-bar").stream().noneMatch(node -> node.isVisible()
                    && node instanceof ScrollBar bar && bar.getOrientation() == javafx.geometry.Orientation.HORIZONTAL), "detail tabs never need horizontal scrolling");
            return null;
        });
    }

    private static boolean loading(Object page) throws Exception { return (boolean) field(page, "loading"); }
    private static Object field(Object value, String name) throws Exception {
        var field = value.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(value);
    }
    private static String labels(Node node) {
        StringBuilder text = new StringBuilder(node instanceof Label label ? label.getText() + "\n" : "");
        if (node instanceof Parent parent) { for (Node child : parent.getChildrenUnmodifiable()) { text.append(labels(child)); } }
        return text.toString();
    }
    private static void snapshot(Stage stage, String name) throws Exception {
        Thread.sleep(180);
        BufferedImage image = fx(() -> {
            stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
            var pixels = stage.getScene().snapshot(null);
            BufferedImage output = new BufferedImage((int) pixels.getWidth(), (int) pixels.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int row = 0; row < output.getHeight(); row++) { for (int column = 0; column < output.getWidth(); column++) {
                output.setRGB(column, row, pixels.getPixelReader().getArgb(column, row));
            } }
            return output;
        });
        Path folder = Path.of("target", "auction-sessions"); Files.createDirectories(folder);
        ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } });
        return result.get(20, TimeUnit.SECONDS);
    }
    private static void waitFor(Callable<Boolean> condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) { if (fx(condition)) { return; } Thread.sleep(40); }
        throw new AssertionError("Timeout: " + message);
    }
}
