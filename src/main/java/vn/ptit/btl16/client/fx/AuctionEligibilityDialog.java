package vn.ptit.btl16.client.fx;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import vn.ptit.btl16.common.fx.DesktopTheme;

/** A recoverable catalog notice with a real retry action and a modal, dimmed background. */
final class AuctionEligibilityDialog extends Dialog<Boolean> {
    private Node background;
    private Effect previousEffect;
    private GaussianBlur blur;
    private Region shade;

    AuctionEligibilityDialog(Stage owner) {
        initOwner(owner); initModality(Modality.WINDOW_MODAL); initStyle(StageStyle.TRANSPARENT);
        setTitle("Chưa có sản phẩm đủ điều kiện");
        Label title = DesktopTheme.label("Chưa có sản phẩm đủ điều kiện", "eligibility-title"); title.setAlignment(Pos.CENTER); title.setMaxWidth(Double.MAX_VALUE);
        Label note = DesktopTheme.label("Chỉ sản phẩm chưa giao dịch và chưa có phiên đang mở mới có thể tạo phòng đấu giá.", "eligibility-note"); note.setAlignment(Pos.CENTER); note.setMaxWidth(320);
        Button retry = new Button("Thử lại ngay", MarketplaceIcons.icon("refresh", 16, "#fff8e8")); retry.setId("auction-eligibility-retry"); retry.getStyleClass().add("eligibility-retry"); retry.setDefaultButton(true); retry.setOnAction(event -> { setResult(true); close(); });
        HBox divider = new HBox(12, new Separator(), MarketplaceIcons.icon("sparkles", 14, "#b48036"), new Separator()); divider.setAlignment(Pos.CENTER); divider.getStyleClass().add("eligibility-divider"); for (Node child : divider.getChildren()) { if (child instanceof Separator) { HBox.setHgrow(child, Priority.ALWAYS); } }
        VBox content = new VBox(11, artwork(), title, note, divider, retry); content.setAlignment(Pos.CENTER); content.getStyleClass().add("eligibility-content");
        StackPane card = new StackPane(); card.getStyleClass().add("eligibility-card");
        Pane waves = new Pane(); waves.setManaged(false); waves.setMouseTransparent(true);
        for (int i = 0; i < 3; i++) {
            Ellipse wave = new Ellipse(310 + i * 15, 170 + i * 13); wave.setFill(Color.TRANSPARENT); wave.setStroke(Color.web(i == 1 ? "#d7a74f" : "#fffdf5", i == 1 ? .4 : .9)); wave.setStrokeWidth(i == 1 ? 12 : 3); wave.setRotate(-22); wave.centerXProperty().bind(card.widthProperty().multiply(.57)); wave.centerYProperty().bind(card.heightProperty().add(43 + i * 10)); waves.getChildren().add(wave);
        }
        Button close = new Button("", MarketplaceIcons.icon("close", 15, "#75542e")); close.setId("auction-eligibility-close"); close.setAccessibleText("Đóng thông báo"); close.getStyleClass().add("eligibility-close"); close.setOnAction(event -> { setResult(false); close(); }); StackPane.setAlignment(close, Pos.TOP_RIGHT); StackPane.setMargin(close, new Insets(10));
        card.getChildren().setAll(waves, content, close);
        Rectangle clip = new Rectangle(); clip.widthProperty().bind(card.widthProperty()); clip.heightProperty().bind(card.heightProperty()); clip.setArcWidth(28); clip.setArcHeight(28); card.setClip(clip);
        DialogPane pane = getDialogPane(); pane.setId("auction-eligibility-dialog"); pane.getStyleClass().add("eligibility-dialog"); pane.setContent(card); pane.getButtonTypes().add(ButtonType.CANCEL);
        Node cancel = pane.lookupButton(ButtonType.CANCEL); cancel.setVisible(false); cancel.setManaged(false); pane.setMinWidth(0); pane.setPrefWidth(430);
        DesktopTheme.dialog(this, owner); pane.setMinWidth(0); setResultConverter(ignored -> false);
        setOnShown(event -> {
            pane.getScene().setFill(Color.TRANSPARENT); Node bar = pane.lookup(".button-bar"); if (bar != null) { bar.setManaged(false); bar.setVisible(false); }
            dimBackground(); Stage window = (Stage) pane.getScene().getWindow();
            Runnable center = () -> { window.setX(owner.getX() + (owner.getWidth() - window.getWidth()) / 2); window.setY(owner.getY() + (owner.getHeight() - window.getHeight()) / 2); };
            window.widthProperty().addListener((o, old, value) -> center.run()); window.heightProperty().addListener((o, old, value) -> center.run());
            window.sizeToScene(); center.run(); Platform.runLater(center); retry.requestFocus();
        });
        setOnHidden(event -> restoreBackground());
    }
    private void dimBackground() {
        Node root = getOwner().getScene().getRoot(); background = root instanceof StackPane shell && !shell.getChildren().isEmpty() ? shell.getChildren().getFirst() : root;
        previousEffect = background.getEffect(); blur = new GaussianBlur(2); blur.setInput(previousEffect); background.setEffect(blur);
        if (root instanceof StackPane shell) { shade = new Region(); shade.setId("auction-eligibility-backdrop"); shade.setStyle("-fx-background-color: rgba(24, 20, 14, .43);"); shade.setMouseTransparent(true); shell.getChildren().add(shade); }
    }
    private void restoreBackground() {
        if (background != null && background.getEffect() == blur) { background.setEffect(previousEffect); }
        if (shade != null && shade.getParent() instanceof Pane parent) { parent.getChildren().remove(shade); }
        background = null; previousEffect = null; shade = null; blur = null;
    }
    private static Pane artwork() {
        Pane art = new Pane(); art.setId("auction-eligibility-artwork"); art.setMinSize(320, 152); art.setPrefSize(320, 152); art.setMaxSize(320, 152); art.setMouseTransparent(true);
        Ellipse glow = new Ellipse(160, 100, 132, 47); glow.setFill(new RadialGradient(0, 0, 160, 100, 130, false, CycleMethod.NO_CYCLE, new Stop(0, Color.web("#e9c38b", .6)), new Stop(1, Color.TRANSPARENT)));
        Ellipse shadow = new Ellipse(160, 139, 121, 7); shadow.setFill(Color.web("#9b6728", .13));
        Rectangle sheet = new Rectangle(116, 24, 110, 107); sheet.setFill(new LinearGradient(0, 0, 1, .5, true, CycleMethod.NO_CYCLE, new Stop(0, Color.web("#d8b17c")), new Stop(.18, Color.web("#fff4d8")), new Stop(.8, Color.web("#f2d9b2")), new Stop(1, Color.web("#cda16b")))); sheet.setArcWidth(6); sheet.setArcHeight(6);
        art.getChildren().addAll(glow, shadow, sheet);
        Paint paperRoll = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, new Stop(0, Color.web("#bf8d51")), new Stop(.42, Color.web("#fff3cf")), new Stop(.7, Color.web("#edd0a4")), new Stop(1, Color.web("#b27c3e")));
        for (int y : new int[]{18, 120}) { Rectangle roll = new Rectangle(111, y, 120, 17); roll.setArcWidth(14); roll.setArcHeight(14); roll.setFill(paperRoll); art.getChildren().add(roll); }
        for (int i = 0; i < 5; i++) { Rectangle line = new Rectangle(130, 48 + i * 12, i % 2 == 0 ? 70 : 58, 3); line.setFill(Color.web("#c5a578", .48)); line.setArcWidth(3); line.setArcHeight(3); art.getChildren().add(line); }
        Group hammer = AuctionCreationDialog.goldGavel(); hammer.setLayoutX(40); hammer.setLayoutY(43); hammer.setOpacity(1); hammer.setRotate(-13); art.getChildren().add(hammer);
        Circle rim = new Circle(176, 77, 27, Color.web("#bf642e")); Circle warning = new Circle(175, 74, 25, new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE, new Stop(0, Color.web("#ff9968")), new Stop(.5, Color.web("#f35b38")), new Stop(1, Color.web("#bc341f")))); warning.setStroke(Color.web("#ffd0a0")); warning.setStrokeWidth(2);
        Text mark = new Text(168, 90, "!"); mark.setFont(Font.font("Be Vietnam Pro", FontWeight.BOLD, 38)); mark.setFill(Color.web("#fff9e9")); art.getChildren().addAll(rim, warning, mark);
        Group magnifier = new Group(); Rectangle handle = new Rectangle(246, 112, 13, 40); handle.setArcWidth(8); handle.setArcHeight(8); handle.setRotate(-35); handle.setFill(paperRoll);
        Circle lens = new Circle(234, 106, 22, Color.web("#fff7df", .7)); lens.setStroke(Color.web("#a76e2c")); lens.setStrokeWidth(6); Circle highlight = new Circle(233, 105, 19, Color.TRANSPARENT); highlight.setStroke(Color.web("#ffe5ac")); highlight.setStrokeWidth(3); magnifier.getChildren().addAll(handle, lens, highlight); art.getChildren().add(magnifier);
        for (double[] sparkle : new double[][]{{260, 42, 20}, {96, 34, 13}, {279, 73, 11}, {67, 104, 11}}) { Node star = MarketplaceIcons.icon("sparkles", sparkle[2], "#d6a254"); star.setLayoutX(sparkle[0]); star.setLayoutY(sparkle[1]); art.getChildren().add(star); }
        return art;
    }
}
