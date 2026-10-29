package vn.ptit.btl16.client.fx;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.service.ApiResponse;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.fx.WalletUi;
import vn.ptit.btl16.common.protocol.MessageType;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Lifetime seller revenue, with the same podium benefits used by server settlement. */
public final class AuctionRankingWindow {
    private final Stage stage = new Stage();
    private final VBox rows = new VBox(10);
    private final Label status = DesktopTheme.label("Đang tải bảng xếp hạng…", "revenue-status");
    private final Button refresh = AccountUi.button("Làm mới", "refresh");
    private final Timeline poll = new Timeline();
    private final ClientTransport transport;
    private final BooleanSupplier sameSession;
    private boolean loading;
    private long request;
    private List<Map<String, String>> previous = List.of();

    public AuctionRankingWindow(Window owner, ClientTransport transport, BooleanSupplier sameSession) {
        this.transport = transport; this.sameSession = sameSession;
        stage.initOwner(owner); stage.initModality(Modality.WINDOW_MODAL); stage.setTitle("Top 10 doanh thu");
        refresh.getStyleClass().add("revenue-refresh"); refresh.setId("revenue-ranking-refresh"); refresh.setMinWidth(132);
        Button close = AccountUi.close(stage::close); close.setId("revenue-ranking-close");
        Label title = DesktopTheme.label("Top 10 doanh thu", "revenue-title");
        Label description = DesktopTheme.label("Vinh danh 10 người bán có tổng doanh thu đấu giá thành công cao nhất. Doanh thu là tổng giá chốt trước khi trừ thuế.", "revenue-description");
        HBox update = new HBox(7, MarketplaceIcons.icon("clock", 15, "#aa6a22"), status); update.setAlignment(Pos.CENTER_LEFT); update.getStyleClass().add("revenue-update");
        VBox text = new VBox(9, title, description, update); text.setMinWidth(0); HBox.setHgrow(text, Priority.ALWAYS);
        VBox controls = new VBox(15, close, refresh); controls.setAlignment(Pos.TOP_RIGHT);
        HBox heading = new HBox(16, new RankingArtwork(0, 120), text, controls); heading.setAlignment(Pos.CENTER_LEFT); heading.getStyleClass().add("revenue-heading");
        StackPane hero = new StackPane(); hero.getStyleClass().add("revenue-hero"); hero.setMinHeight(170);
        Pane decoration = new Pane(); decoration.setMouseTransparent(true);
        Node gavel = MarketplaceIcons.icon("gavel", 125, "#b67a25"); gavel.setOpacity(.13); gavel.setRotate(-18); gavel.layoutXProperty().bind(hero.widthProperty().subtract(160)); gavel.setLayoutY(30); decoration.getChildren().add(gavel);
        for (int index = 0; index < 12; index++) {
            var sparkle = MarketplaceIcons.icon("star", index % 3 == 0 ? 13 : 7, "#fff9db");
            sparkle.layoutXProperty().bind(hero.widthProperty().multiply((index + .4) / 12)); sparkle.setLayoutY(12 + index * 37 % 145); decoration.getChildren().add(sparkle);
        }
        hero.getChildren().addAll(decoration, heading); AccountUi.draggable(heading, stage);
        ScrollPane scroll = new ScrollPane(rows); scroll.setId("revenue-ranking-scroll"); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.getStyleClass().add("revenue-scroll");
        rows.setId("revenue-ranking-rows"); rows.setPadding(new Insets(5, 8, 8, 5));
        Label tax = DesktopTheme.label("Thuế trên giá chốt: đến 1 tỷ đồng 10% · Trên 1 tỷ đồng 15%", "revenue-tax-note");
        Label benefits = DesktopTheme.label("Giảm thuế: Top 1 −5 điểm % · Top 2 −4 · Top 3 −3 · Top 4–5 −2 · Top 6–10 −1", "revenue-benefits");
        Label timing = DesktopTheme.label("Thứ hạng được lấy trước khi chốt phiên. Bằng doanh thu: người bán có phiên thành công đầu tiên sớm hơn xếp trước, sau đó theo mã tài khoản.", "revenue-footnote");
        VBox footer = new VBox(4, tax, benefits, timing); footer.getStyleClass().add("revenue-footer");
        BorderPane content = new BorderPane(scroll, hero, null, footer, null); content.getStyleClass().add("revenue-page");
        content.widthProperty().addListener((o, before, width) -> content.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("compact"), width.doubleValue() < 850));
        AccountUi.window(stage, content, 920, 830); stage.setMinWidth(780); stage.setMinHeight(540);
        refresh.setOnAction(event -> load());
        poll.getKeyFrames().add(new KeyFrame(Duration.seconds(5), event -> load())); poll.setCycleCount(Timeline.INDEFINITE);
        stage.setOnHidden(event -> { poll.stop(); request++; loading = false; rows.getChildren().clear(); previous = List.of(); });
    }

    private void load() {
        if (!sameSession.getAsBoolean()) { stage.close(); return; }
        if (loading) { return; }
        if (!transport.isConnected()) { status.setText("Mất kết nối. Đang chờ kết nối lại…"); return; }
        loading = true; refresh.setDisable(true); long expected = ++request;
        transport.sendRequest(MessageType.GET_REVENUE_RANKING, Map.of()).whenComplete((message, error) -> Platform.runLater(() -> {
            if (request != expected || !stage.isShowing()) { return; }
            loading = false; refresh.setDisable(false);
            if (!sameSession.getAsBoolean()) { stage.close(); return; }
            if (error != null || message == null || !new ApiResponse(message).isSuccess()) {
                status.setText("Chưa tải được bảng xếp hạng. Nhấn Làm mới để thử lại."); return;
            }
            ApiResponse response = new ApiResponse(message);
            try {
                int count = Math.min(10, Math.max(0, Integer.parseInt(response.get("count"))));
                List<Map<String, String>> values = new ArrayList<>();
                for (int index = 0; index < count; index++) {
                    String prefix = "ranking." + index + "."; Map<String, String> value = new LinkedHashMap<>();
                    for (String key : List.of("sellerId", "username", "displayName", "revenue", "saleCount", "avatarBase64", "discountRate")) { value.put(key, response.get(prefix + key)); }
                    values.add(value);
                }
                if (!values.equals(previous) || rows.getChildren().isEmpty()) {
                    List<Node> updated = new ArrayList<>();
                    for (int index = 0; index < count; index++) { updated.add(row(index + 1, values.get(index))); }
                    if (count == 0) { Label empty = DesktopTheme.label("Chưa có doanh thu đấu giá thành công.\nBảng xếp hạng sẽ xuất hiện sau phiên bán thành công đầu tiên.", "revenue-empty"); empty.setMaxWidth(Double.MAX_VALUE); updated.add(empty); }
                    rows.getChildren().setAll(updated); previous = values;
                }
                status.setText("Tự động cập nhật mỗi 5 giây  ·  Đã có " + count + " người bán trong Top 10");
            } catch (RuntimeException invalid) { status.setText("Dữ liệu xếp hạng chưa hợp lệ. Nhấn Làm mới để thử lại."); }
        }));
    }

    private Node row(int rank, Map<String, String> value) {
        boolean podium = rank <= 3;
        Node medal = podium ? new RankingArtwork(rank, rank == 1 ? 92 : 84) : DesktopTheme.label(Integer.toString(rank), "revenue-position");
        String username = value.get("username");
        String display = value.get("displayName").isBlank() ? username : value.get("displayName");
        Label name = DesktopTheme.label(username, "revenue-name"); name.setWrapText(false); name.setMinWidth(0); name.setTooltip(new Tooltip(display + " (@" + username + ")"));
        Label badge = DesktopTheme.label("−" + percent(value.get("discountRate")) + " điểm % thuế", "revenue-discount");
        FlowPane identity = new FlowPane(8, 4, name, badge); identity.setAlignment(Pos.CENTER_LEFT); identity.setMinWidth(0); identity.setPrefWrapLength(220);
        Label seller = DesktopTheme.label(display + "  ·  Mã #" + value.get("sellerId"), "revenue-metadata"); seller.setWrapText(false); seller.setMinWidth(0);
        HBox sales = new HBox(7, MarketplaceIcons.icon("gavel", 14, "#9f794d"), DesktopTheme.label(value.get("saleCount") + " phiên bán thành công", "revenue-metadata")); sales.setAlignment(Pos.CENTER_LEFT);
        VBox info = new VBox(podium ? 8 : 4, identity, seller, sales); info.setMinWidth(0); HBox.setHgrow(info, Priority.ALWAYS);
        Label money = DesktopTheme.label(WalletUi.money(new BigDecimal(value.get("revenue"))), "revenue-amount ranking-price"); money.setWrapText(false); money.setId("revenue-ranking-amount-" + rank);
        money.setMinWidth(Region.USE_PREF_SIZE); money.setTooltip(new Tooltip(money.getText()));
        HBox amount = new HBox(8, MarketplaceIcons.icon("coins", podium ? 26 : 20, "#b77725"), money); amount.setAlignment(Pos.CENTER_RIGHT);
        VBox price = new VBox(4, DesktopTheme.label("Tổng doanh thu", "revenue-price-caption"), amount); price.setAlignment(Pos.CENTER_RIGHT); price.setMinWidth(Region.USE_PREF_SIZE);
        HBox content = new HBox(podium ? 15 : 12, medal, avatar(value.get("avatarBase64"), display, podium ? 88 : 58), info, price); content.setAlignment(Pos.CENTER_LEFT); content.setPadding(new Insets(podium ? 13 : 10, 20, podium ? 13 : 10, podium ? 10 : 20));
        StackPane card = new StackPane(); card.setId("revenue-ranking-" + rank); card.getStyleClass().add("revenue-row");
        if (podium) { card.getStyleClass().add("revenue-podium-" + rank); }
        Node watermark = MarketplaceIcons.icon(podium ? "trophy" : "coins", podium ? 90 : 48, "#bda171"); watermark.setOpacity(.1); watermark.setRotate(-12); StackPane.setAlignment(watermark, Pos.CENTER_RIGHT); StackPane.setMargin(watermark, new Insets(0, 28, 0, 0));
        card.getChildren().addAll(watermark, content); return card;
    }

    private static Node avatar(String encoded, String name, double size) {
        StackPane box = new StackPane(); box.getStyleClass().add("revenue-avatar"); box.setMinSize(size, size); box.setMaxSize(size, size);
        Label initials = DesktopTheme.label(name.isBlank() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT), "revenue-initial"); box.getChildren().add(initials);
        if (!encoded.isBlank()) {
            try {
                Image image = new Image(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)), size * 2, size * 2, true, true);
                if (!image.isError()) { ImageView view = new ImageView(image); view.setFitWidth(size); view.setFitHeight(size); view.setPreserveRatio(true); box.getChildren().setAll(view); }
            } catch (IllegalArgumentException ignored) { }
        }
        Rectangle clip = new Rectangle(size, size); clip.setArcWidth(14); clip.setArcHeight(14); box.setClip(clip); return box;
    }
    private static String percent(String rate) { return new BigDecimal(rate).multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString(); }
    public void show() { DesktopTheme.fitOnScreen(stage, 920, 830); stage.show(); load(); poll.play(); }
}
