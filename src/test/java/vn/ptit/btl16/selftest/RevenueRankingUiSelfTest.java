package vn.ptit.btl16.selftest;

import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.*;
import vn.ptit.btl16.client.fx.AuctionRankingWindow;
import vn.ptit.btl16.client.network.*;
import vn.ptit.btl16.common.protocol.*;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.util.AuctionFees;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** Podium layout, exact amounts, public avatars, refresh and pending-response lifecycle. */
public final class RevenueRankingUiSelfTest {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        Network network = new Network(); AtomicBoolean session = new AtomicBoolean(true);
        try {
            Stage owner = fx(() -> { Stage stage = new Stage(); Scene scene = new Scene(new StackPane(), 1100, 850); DesktopTheme.apply(scene); stage.setScene(scene); stage.show(); return stage; });
            fx(() -> { new AuctionRankingWindow(owner, network, session::get).show(); return null; }); drain();
            Stage board = fx(() -> board());
            fx(() -> {
                Scene scene = board.getScene(); scene.getRoot().applyCss(); scene.getRoot().layout();
                TestSupport.equals(StageStyle.UNDECORATED, board.getStyle(), "ranking uses custom rounded window");
                TestSupport.equals(10, scene.lookup("#revenue-ranking-rows").lookupAll(".revenue-row").size(), "ten sellers rendered");
                TestSupport.check(scene.lookup("#revenue-ranking-1").getStyleClass().contains("revenue-podium-1"), "first card gold");
                TestSupport.check(scene.lookup("#revenue-ranking-2").getStyleClass().contains("revenue-podium-2"), "second card silver");
                TestSupport.check(scene.lookup("#revenue-ranking-3").getStyleClass().contains("revenue-podium-3"), "third card bronze");
                TestSupport.check(scene.lookup("#revenue-ranking-4").getStyleClass().size() == 1, "remaining cards compact");
                TestSupport.check(((Label) scene.lookup("#revenue-ranking-amount-1")).getText().contains("2.500.000.000,25"), "revenue formats billion and cents exactly");
                TestSupport.check(scene.lookup("#revenue-ranking-1").lookupAll(".revenue-avatar").stream().anyMatch(n -> ((StackPane)n).getChildren().stream().anyMatch(c -> c instanceof ImageView view && !view.getImage().isError())), "public avatar renders");
                TestSupport.check(labels(scene.getRoot()).contains("Top 4–5 −2"), "nonoverlapping tax bands shown");
                screenshot(scene, "target/revenue-ranking.png");
                board.setWidth(780); board.setHeight(620); return null;
            }); drain();
            fx(() -> {
                Scene scene = board.getScene(); scene.getRoot().applyCss(); scene.getRoot().layout();
                TestSupport.check(scene.getWidth() <= 780 && scene.getHeight() <= 620, "compact scene actually resized");
                Node first = scene.lookup("#revenue-ranking-1"); Node amount = scene.lookup("#revenue-ranking-amount-1");
                var firstBounds = first.localToScene(first.getBoundsInLocal()); var amountBounds = amount.localToScene(amount.getBoundsInLocal());
                TestSupport.check(amountBounds.getMaxX() <= firstBounds.getMaxX() - 10, "compact width keeps full amount inside card");
                var money = (Label) amount; var measured = new javafx.scene.text.Text(money.getText()); measured.setFont(money.getFont());
                TestSupport.check(money.getWidth() >= measured.getLayoutBounds().getWidth(), "compact amount never shows ellipsis");
                screenshot(scene, "target/revenue-ranking-compact.png");
                network.count = 0; ((Button) scene.lookup("#revenue-ranking-refresh")).fire(); return null;
            }); drain();
            fx(() -> { TestSupport.check(labels(board.getScene().getRoot()).contains("Chưa có doanh thu"), "empty state clears old sellers"); network.count=10; network.failure = true; ((Button) board.getScene().lookup("#revenue-ranking-refresh")).fire(); return null; }); drain();
            fx(() -> { TestSupport.check(labels(board.getScene().getRoot()).contains("Chưa tải được"), "failed request offers retry"); network.failure=false; network.connected=false; ((Button) board.getScene().lookup("#revenue-ranking-refresh")).fire(); TestSupport.check(labels(board.getScene().getRoot()).contains("Mất kết nối"), "disconnect explains stale board"); network.connected=true; ((Button) board.getScene().lookup("#revenue-ranking-refresh")).fire(); return null; }); drain();
            fx(() -> { TestSupport.check(board.getScene().lookup("#revenue-ranking-10") != null, "refresh recovers rows"); network.pending = new CompletableFuture<>(); ((Button) board.getScene().lookup("#revenue-ranking-refresh")).fire(); ((Button) board.getScene().lookup("#revenue-ranking-close")).fire(); network.pending.complete(network.reply()); return null; }); drain();
            fx(() -> { TestSupport.equals(0, board.getScene().lookup("#revenue-ranking-rows").lookupAll(".revenue-row").size(), "late response after close cannot restore private view"); network.pending=null; new AuctionRankingWindow(owner, network, session::get).show(); return null; }); drain();
            fx(() -> { session.set(false); ((Button) board().getScene().lookup("#revenue-ranking-refresh")).fire(); TestSupport.check(Window.getWindows().stream().noneMatch(w -> w instanceof Stage stage && "Top 10 doanh thu".equals(stage.getTitle()) && stage.isShowing()), "session change closes board"); return null; });
            System.out.println("[PASS] RevenueRankingUiSelfTest: gold/silver/bronze, ten seller totals, real avatar, compact sizing, empty/error/disconnect/retry, closed/session invalidation and screenshots");
        } finally { fx(() -> { for (Window window : List.copyOf(Window.getWindows())) { window.hide(); } return null; }); Platform.exit(); }
    }
    private static Stage board() { return Window.getWindows().stream().filter(w -> w instanceof Stage s && "Top 10 doanh thu".equals(s.getTitle()) && s.isShowing()).map(w -> (Stage)w).findFirst().orElseThrow(); }
    private static String labels(Node root) { StringBuilder result = new StringBuilder(); if (root instanceof Label label) { result.append(label.getText()); } if (root instanceof Parent parent) { for (Node child : parent.getChildrenUnmodifiable()) { result.append('\n').append(labels(child)); } } return result.toString(); }
    private static <T> T fx(Callable<T> task) throws Exception { FutureTask<T> future = new FutureTask<>(task); Platform.runLater(future); return future.get(15, TimeUnit.SECONDS); }
    private static void drain() throws Exception { fx(() -> null); Thread.sleep(150); fx(() -> null); }
    private static void screenshot(Scene scene, String path) throws Exception {
        var pixels = scene.snapshot(null); var output = new java.awt.image.BufferedImage((int)pixels.getWidth(), (int)pixels.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<output.getHeight(); y++) { for (int x=0; x<output.getWidth(); x++) { output.setRGB(x,y,pixels.getPixelReader().getArgb(x,y)); } }
        javax.imageio.ImageIO.write(output,"png",new java.io.File(path));
    }
    private static final class Network implements ClientTransport {
        int count=10; boolean connected=true, failure; CompletableFuture<WireMessage> pending;
        WireMessage reply() {
            Map<String,String> data = new HashMap<>(); data.put("success", Boolean.toString(!failure)); data.put("count", Integer.toString(count));
            String[] names = {"ngocphuoc", "minhanh", "hoangnam", "thanhtruc", "nguyenkhang", "baotran", "quocbao", "tuananh", "linhchi", "haiphong"};
            String[] display = {"Trần Ngọc Phước", "Nguyễn Minh Anh", "Lê Hoàng Nam", "Đỗ Thanh Trúc", "Nguyễn Khang", "Phạm Bảo Trân", "Ngô Quốc Bảo", "Trần Tuấn Anh", "Nguyễn Linh Chi", "Lê Hải Phong"};
            for (int index=0; index<count; index++) {
                String prefix="ranking."+index+".";
                data.put(prefix+"sellerId", Integer.toString(index+1)); data.put(prefix+"username",names[index]); data.put(prefix+"displayName",display[index]);
                data.put(prefix+"revenue", index==0 ? "2500000000.25" : Integer.toString(900000000-index*70000000)); data.put(prefix+"saleCount",Integer.toString(30-index*2)); data.put(prefix+"discountRate", AuctionFees.discount(index+1).toPlainString());
                data.put(prefix+"avatarBase64",index==0 ? vn.ptit.btl16.server.social.AvatarImages.normalize(Base64.getEncoder().encodeToString(TestMedia.png())) : "");
            }
            return WireMessage.response(MessageType.REVENUE_RANKING_RESULT, null, 1, data);
        }
        public CompletableFuture<WireMessage> sendRequest(MessageType type, Map<String,String> data) { TestSupport.equals(MessageType.GET_REVENUE_RANKING,type,"uses seller revenue API"); return pending==null ? CompletableFuture.completedFuture(reply()) : pending; }
        public CompletableFuture<Void> connect() { return CompletableFuture.completedFuture(null); }
        public void disconnect() { connected=false; }
        public boolean isConnected() { return connected; }
        public ConnectionState getState() { return connected ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED; }
        public String getEndpoint() { return "test"; } public void setEndpoint(String endpoint) { }
        public CompletableFuture<WireMessage> ping() { return sendRequest(MessageType.GET_REVENUE_RANKING,Map.of()); }
        public void addEventListener(Consumer<WireMessage> listener) { } public void addStateListener(ConnectionStateListener listener) { } public void close() { }
    }
}
