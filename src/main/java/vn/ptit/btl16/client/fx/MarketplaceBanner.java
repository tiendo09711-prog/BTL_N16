package vn.ptit.btl16.client.fx;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import vn.ptit.btl16.common.fx.DesktopTheme;

final class MarketplaceBanner extends StackPane {
    MarketplaceBanner(Label title) {
        setId("marketplace-banner"); getStyleClass().add("market-hero");
        setMinHeight(68); setPrefHeight(68); setMaxHeight(68);
        Pane art = new Pane(); art.setMouseTransparent(true);
        Ellipse ribbon = new Ellipse(275, 78); ribbon.setFill(Color.web("#ddbc7c", .15));
        ribbon.setRotate(-17); ribbon.centerXProperty().bind(widthProperty().subtract(70)); ribbon.setCenterY(75);
        Ellipse line = new Ellipse(310, 86); line.setFill(Color.TRANSPARENT); line.setStroke(Color.web("#fffaf1", .7));
        line.setRotate(-17); line.centerXProperty().bind(widthProperty().subtract(75)); line.setCenterY(60);
        var hammer = AuctionCreationDialog.goldGavel(); hammer.setOpacity(1);
        hammer.layoutXProperty().bind(widthProperty().multiply(.64)); hammer.setLayoutY(-10);
        art.getChildren().addAll(ribbon, line, hammer);
        Label subtitle = DesktopTheme.label("Khám phá những sản phẩm độc đáo và tham gia đấu giá để sở hữu ngay hôm nay!", "market-hero-subtitle");
        subtitle.setWrapText(false); subtitle.setMinWidth(0);
        VBox identity = new VBox(3, title, subtitle); identity.setMinWidth(0); HBox.setHgrow(identity, Priority.ALWAYS);
        Label slogan = new Label("Sàn đồ chất\nGiá trị thật"); slogan.getStyleClass().add("market-hero-slogan");
        slogan.setMinWidth(210); slogan.setAlignment(Pos.CENTER);
        HBox content = new HBox(15, MarketplaceIcons.icon("gavel", 39, "#e8bd72"), identity, slogan);
        content.setAlignment(Pos.CENTER_LEFT); content.getStyleClass().add("market-hero-content");
        getChildren().addAll(art, content);
        Rectangle clip = new Rectangle(); clip.widthProperty().bind(widthProperty()); clip.heightProperty().bind(heightProperty());
        clip.setArcWidth(18); clip.setArcHeight(18); setClip(clip);
        widthProperty().addListener((observable, before, width) -> { boolean show = width.doubleValue() >= 760; slogan.setVisible(show); slogan.setManaged(show); });
    }
}
