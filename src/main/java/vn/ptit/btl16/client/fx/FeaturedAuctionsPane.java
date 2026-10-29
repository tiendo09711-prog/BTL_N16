package vn.ptit.btl16.client.fx;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import vn.ptit.btl16.client.model.*;
import vn.ptit.btl16.common.fx.DesktopTheme;
import vn.ptit.btl16.common.fx.WalletUi;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

/** Compact, server-ranked room recommendations above the regular product catalog. */
final class FeaturedAuctionsPane extends VBox implements AutoCloseable {
    private final GridPane grid = new GridPane();
    private final Label message = DesktopTheme.label("Đang tải phiên nổi bật…", "account-note");
    private final Map<Long, Card> cards = new LinkedHashMap<>();
    private Function<ClientAuction, CompletableFuture<Image>> covers = a -> CompletableFuture.completedFuture(null);
    private Consumer<ClientAuction> enter = a -> { };
    private Supplier<Instant> clock = Instant::now;

    FeaturedAuctionsPane() {
        setId("featured-auctions"); getStyleClass().add("featured-auctions"); setSpacing(10); setMinWidth(0);
        StackPane emblem = new StackPane(MarketplaceIcons.icon("crown", 30, "#ffe2a0")); emblem.getStyleClass().add("featured-emblem");
        Label kicker = DesktopTheme.label("TÂM ĐIỂM SÀN ĐẤU GIÁ", "featured-kicker");
        Label title = DesktopTheme.label("Phiên nổi bật", "featured-heading");
        VBox copy = new VBox(3, kicker, title); copy.setMinWidth(0); HBox.setHgrow(copy, Priority.ALWAYS);
        Label help = new Label(); help.setGraphic(MarketplaceIcons.icon("info", 19, "#f4d59c")); help.getStyleClass().add("featured-help");
        String criteria = "Điểm nổi bật = số người đang trong phòng + lượt ra giá hợp lệ + lượt vào phòng. Mỗi người đang ở trong phòng và mỗi lượt vào hoặc ra giá hợp lệ tính 1 điểm. Rời phòng không cộng điểm; phòng trống không có điểm số người. Cập nhật mỗi 5 giây.";
        help.setTooltip(new Tooltip(criteria)); help.setAccessibleText("Cách xếp hạng. " + criteria);
        HBox header = new HBox(12, emblem, copy, MarketplaceIcons.icon("sparkles", 24, "#d6ab62"), help);
        header.setAlignment(Pos.CENTER_LEFT); header.getStyleClass().add("featured-header");
        Label note = DesktopTheme.label("Khám phá những phiên đấu giá đang được quan tâm nhất", "featured-intro");
        note.setGraphic(MarketplaceIcons.icon("flame", 15, "#bf6b29"));
        getChildren().addAll(header, note, message, grid);
        grid.setHgap(10); grid.setVgap(10); grid.setId("featured-auctions-grid"); widthProperty().addListener((o, old, value) -> arrange());
    }
    void configure(Function<ClientAuction, CompletableFuture<Image>> loader, Supplier<Instant> now, Consumer<ClientAuction> action) { covers = loader; clock = now; enter = action; }
    void setData(Map<String, String> data) {
        var values = ClientWireParser.auctions(data);
        var previous = new HashMap<>(cards); cards.clear();
        for (int i = 0; i < values.size() && cards.size() < 5; i++) {
            ClientAuction auction = values.get(i);
            if (!auction.isOpen() || auction.getStartTime() == null || clock.get().isBefore(auction.getStartTime()) || !clock.get().isBefore(auction.getEndTime())) { continue; }
            Card card = previous.remove(auction.getAuctionId());
            if (card == null) { card = new Card(); }
            card.update(auction, cards.size() + 1, data, "auction." + i + "."); cards.put(auction.getAuctionId(), card);
        }
        previous.values().forEach(Card::dispose); message.setText(cards.isEmpty() ? "Chưa có phiên công khai đang diễn ra." : "");
        message.setVisible(cards.isEmpty()); message.setManaged(cards.isEmpty()); arrange();
    }
    void unavailable() { if (cards.isEmpty()) { message.setText("Chưa tải được phiên nổi bật. Bấm Làm mới để thử lại."); } }
    void tick() {
        boolean removed = cards.values().removeIf(card -> {
            if (!clock.get().isBefore(card.auction.getEndTime())) { card.dispose(); return true; } return false;
        });
        if (removed) { arrange(); message.setText("Chưa có phiên công khai đang diễn ra."); message.setVisible(cards.isEmpty()); message.setManaged(cards.isEmpty()); }
    }
    private void arrange() {
        int columns = getWidth() >= 1140 ? 5 : getWidth() >= 690 ? 3 : getWidth() >= 430 ? 2 : 1;
        grid.getChildren().clear(); grid.getColumnConstraints().clear();
        for (int i = 0; i < columns; i++) { var c = new ColumnConstraints(); c.setPercentWidth(100.0 / columns); c.setMinWidth(0); c.setHgrow(Priority.ALWAYS); grid.getColumnConstraints().add(c); }
        int i = 0; for (Card card : cards.values()) { grid.add(card.button, i % columns, i / columns); i++; }
    }
    @Override public void close() { cards.values().forEach(Card::dispose); cards.clear(); grid.getChildren().clear(); message.setText("Chưa có phiên công khai đang diễn ra."); message.setVisible(true); message.setManaged(true); }

    private final class Card {
        final VBox body = new VBox(7);
        final Button button = new Button() {
            @Override public javafx.geometry.Orientation getContentBias() { return javafx.geometry.Orientation.HORIZONTAL; }
            @Override protected double computePrefHeight(double width) { return body.prefHeight(width < 0 ? 200 : Math.max(1, width - 22)) + 22; }
        };
        final Label rank = new Label(), room = new Label(), leader = new Label("DẪN ĐẦU"), title = new Label(), price = new Label(), points = new Label();
        final Label people = metric("users"), bids = metric("gavel"), movement = metric("exchange");
        final ImageView image = new ImageView();
        final StackPane cover = new StackPane(MarketplaceIcons.icon("box", 26, "#b29b7d"), image);
        ClientAuction auction; String imageKey = ""; long generation;
        Card() {
            button.getStyleClass().add("featured-card"); button.setMinSize(0, 0); button.setMaxWidth(Double.MAX_VALUE); button.setMaxHeight(Region.USE_PREF_SIZE); button.setAlignment(Pos.TOP_LEFT);
            GridPane.setFillHeight(button, false); GridPane.setValignment(button, javafx.geometry.VPos.TOP);
            cover.setMinSize(0, 100); cover.setPrefHeight(110); cover.setMaxHeight(110); cover.getStyleClass().add("featured-cover");
            image.setFitHeight(102); image.setPreserveRatio(true); image.fitWidthProperty().bind(cover.widthProperty().subtract(10));
            rank.getStyleClass().add("featured-rank"); rank.setGraphicTextGap(5);
            Label live = DesktopTheme.label("● TRỰC TIẾP", "featured-live");
            HBox badges = new HBox(5, rank, DesktopTheme.spacer(), live); badges.setAlignment(Pos.TOP_LEFT); badges.setPickOnBounds(false);
            StackPane.setAlignment(badges, Pos.TOP_CENTER); StackPane.setMargin(badges, new Insets(7));
            room.getStyleClass().add("featured-room"); leader.getStyleClass().add("featured-leader");
            leader.setGraphic(MarketplaceIcons.icon("flame", 11, "#fff5d9"));
            HBox tags = new HBox(5, room, DesktopTheme.spacer(), leader); tags.setAlignment(Pos.BOTTOM_LEFT); tags.setPickOnBounds(false);
            StackPane.setAlignment(tags, Pos.BOTTOM_CENTER); StackPane.setMargin(tags, new Insets(7)); cover.getChildren().addAll(badges, tags);
            title.getStyleClass().add("featured-title"); title.setWrapText(true); title.setMaxWidth(Double.MAX_VALUE);
            price.getStyleClass().add("featured-price"); price.setWrapText(true);
            FlowPane metrics = new FlowPane(5, 5, people, bids, movement); metrics.setMinWidth(0);
            points.getStyleClass().add("featured-points"); points.setWrapText(true); points.setGraphic(MarketplaceIcons.icon("star", 14, "#a66a1b")); points.setGraphicTextGap(6);
            Label action = DesktopTheme.label("Vào phòng", "featured-enter"); action.setGraphic(MarketplaceIcons.icon("enter", 15, "#fff7df")); action.setGraphicTextGap(8);
            action.setMaxWidth(Double.MAX_VALUE); action.setAlignment(Pos.CENTER);
            body.getChildren().setAll(cover, title, price, metrics, points, action); body.setMinWidth(0); body.setMaxHeight(Region.USE_PREF_SIZE); body.prefWidthProperty().bind(button.widthProperty().subtract(22));
            button.setGraphic(body); button.setOnAction(event -> { if (auction != null && clock.get().isBefore(auction.getEndTime())) { enter.accept(auction); } });
        }
        void update(ClientAuction a, int position, Map<String, String> data, String prefix) {
            auction = a; button.setId("featured-auction-" + a.getAuctionId()); rank.setText("TOP " + position); room.setText("PHÒNG #" + a.getAuctionId());
            button.getStyleClass().removeIf(style -> style.startsWith("featured-card-top-")); button.getStyleClass().add("featured-card-top-" + position);
            rank.setGraphic(MarketplaceIcons.icon(position == 1 ? "crown" : position <= 3 ? "trophy" : "star", 15, position == 2 ? "#445565" : "#77501c"));
            leader.setVisible(position == 1); leader.setManaged(position == 1);
            title.setText(a.getProductName()); price.setText(WalletUi.money(a.getCurrentPrice()));
            people.setText(a.getWatcherCount() + " người"); bids.setText(data.getOrDefault(prefix + "totalBids", "0") + " lượt ra giá"); movement.setText(data.getOrDefault(prefix + "movementCount", "0") + " lượt vào");
            points.setText(data.getOrDefault(prefix + "featuredScore", "0") + " điểm nổi bật");
            button.setAccessibleText(rank.getText() + ", " + room.getText() + ", " + title.getText() + ", " + people.getText() + ", " + bids.getText() + ", " + movement.getText() + ", " + points.getText() + ". Vào phòng.");
            String key = a.getProductId() + ":" + a.getImageVersion() + ":" + a.hasImage(); if (imageKey.equals(key)) { return; }
            imageKey = key; long token = ++generation; image.setImage(null);
            if (a.hasImage()) { covers.apply(a).whenComplete((value, error) -> Platform.runLater(() -> { if (token == generation && error == null && value != null && !value.isError()) { image.setImage(value); } })); }
        }
        void dispose() { generation++; image.setImage(null); }
    }

    private static Label metric(String icon) {
        Label label = new Label(); label.getStyleClass().add("featured-metric"); label.setGraphic(MarketplaceIcons.icon(icon, 12, "#937345")); label.setGraphicTextGap(4); return label;
    }
}
