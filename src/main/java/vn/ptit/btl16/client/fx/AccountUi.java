package vn.ptit.btl16.client.fx;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import vn.ptit.btl16.common.fx.DesktopTheme;

/** Shared ivory cards and gold controls for personal account screens. */
final class AccountUi {
    private AccountUi() { }
    static StackPane emblem(String icon, double size) {
        StackPane frame = new StackPane(MarketplaceIcons.icon(icon, size * .49, "#92602a"));
        frame.getStyleClass().add("account-emblem"); frame.setMinSize(size, size); frame.setMaxSize(size, size); return frame;
    }
    static HBox heading(String title, String subtitle, String icon, Button close) {
        VBox copy = new VBox(5, DesktopTheme.label(title, "account-title"), DesktopTheme.label(subtitle, "account-note"));
        copy.setMinWidth(0); HBox.setHgrow(copy, Priority.ALWAYS);
        HBox row = new HBox(14, emblem(icon, 48), copy); row.setAlignment(Pos.CENTER_LEFT);
        if (close != null) { row.getChildren().add(close); } return row;
    }
    static HBox sectionHeading(String title, String note, String icon) {
        VBox text = new VBox(4, DesktopTheme.label(title, "account-section-title"), DesktopTheme.label(note, "account-note"));
        text.setMinWidth(0); HBox.setHgrow(text, Priority.ALWAYS);
        HBox heading = new HBox(10, emblem(icon, 42), text); heading.setAlignment(Pos.CENTER_LEFT); return heading;
    }
    static VBox card(Node... nodes) { VBox card = new VBox(12, nodes); card.getStyleClass().add("account-card"); card.setMinWidth(0); return card; }
    static Button button(String text, String icon) {
        Button button = new Button(text, MarketplaceIcons.icon(icon, 15, "#83582b")); button.getStyleClass().add("account-button"); return button;
    }
    static Button close(Runnable action) {
        Button close = button("", "close"); close.getStyleClass().add("account-close"); close.setAccessibleText("Đóng"); close.setOnAction(event -> action.run()); return close;
    }
    static HBox hint(String text) {
        Label copy = DesktopTheme.label(text, "account-hint-text"); copy.setMinWidth(0); HBox.setHgrow(copy, Priority.ALWAYS);
        HBox hint = new HBox(10, MarketplaceIcons.icon("info", 18, "#bd8938"), copy); hint.setAlignment(Pos.TOP_LEFT); hint.getStyleClass().add("account-hint"); return hint;
    }
    static StackPane banner(String title, String subtitle, String slogan) {
        StackPane banner = new StackPane(); banner.getStyleClass().add("account-banner"); banner.setMinHeight(92); banner.setMaxHeight(92);
        Pane artwork = new Pane(); artwork.setMouseTransparent(true);
        for (int i = 0; i < 2; i++) {
            Ellipse line = new Ellipse(290 + i * 30, 72 + i * 17); line.setFill(Color.TRANSPARENT); line.setStroke(Color.web("#fffaf0", .65)); line.setRotate(-23);
            line.centerXProperty().bind(banner.widthProperty().subtract(55)); line.setCenterY(70); artwork.getChildren().add(line);
        }
        var hammer = MarketplaceIcons.icon("gavel", 86, "#bb8e4b"); hammer.setOpacity(.16); hammer.setRotate(-16); hammer.layoutXProperty().bind(banner.widthProperty().multiply(.55)); hammer.setLayoutY(15); artwork.getChildren().add(hammer);
        Label name = DesktopTheme.label(title, "account-banner-title"); name.setWrapText(false); name.setMinWidth(0); name.setTooltip(new Tooltip(title));
        VBox identity = new VBox(5, name, DesktopTheme.label(subtitle, "account-banner-subtitle")); identity.setMinWidth(0); HBox.setHgrow(identity, Priority.ALWAYS);
        Label tagline = DesktopTheme.label(slogan, "account-banner-slogan"); tagline.setMinWidth(190);
        HBox content = new HBox(17, emblem("wallet", 66), identity, tagline); content.setAlignment(Pos.CENTER_LEFT); content.getStyleClass().add("account-banner-content");
        banner.widthProperty().addListener((o, before, width) -> { boolean show = width.doubleValue() >= 760; tagline.setManaged(show); tagline.setVisible(show); });
        banner.getChildren().addAll(artwork, content); Rectangle clip = new Rectangle(); clip.widthProperty().bind(banner.widthProperty()); clip.heightProperty().bind(banner.heightProperty()); clip.setArcWidth(22); clip.setArcHeight(22); banner.setClip(clip); return banner;
    }
    static void window(Stage stage, Region root, double width, double height) {
        root.getStyleClass().addAll("account-page", "account-modal"); stage.initStyle(StageStyle.UNDECORATED);
        Scene scene = new Scene(DesktopTheme.viewport(root), width, height); DesktopTheme.apply(scene); stage.setScene(scene);
        scene.setOnKeyPressed(event -> { if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) { stage.close(); } });
        stage.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWN, event -> dimOwner(stage));
    }
    static void dimOwner(Stage stage) {
        var owner = stage.getOwner();
        if (owner == null || owner.getScene() == null || !(owner.getScene().getRoot() instanceof StackPane shell)) { return; }
        Region shade = new Region(); shade.setStyle("-fx-background-color: rgba(39, 29, 18, 0.19);"); shade.setMouseTransparent(true);
        shell.getChildren().add(shade); stage.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, event -> shell.getChildren().remove(shade));
    }
    static void draggable(Node header, Stage stage) {
        double[] offset = new double[2];
        header.setOnMousePressed(event -> { offset[0] = event.getScreenX() - stage.getX(); offset[1] = event.getScreenY() - stage.getY(); });
        header.setOnMouseDragged(event -> { stage.setX(event.getScreenX() - offset[0]); stage.setY(event.getScreenY() - offset[1]); });
    }
}
