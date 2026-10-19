package vn.ptit.btl16.common.fx;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.stage.Window;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

public final class DesktopTheme {
    private static final String CSS = DesktopTheme.class.getResource("/ui/auction.css").toExternalForm();
    private static boolean fontsLoaded;

    private DesktopTheme() {
    }

    public static void apply(Scene scene) {
        if (!fontsLoaded) {
            for (String weight : new String[]{"Regular", "SemiBold", "Bold"}) {
                var stream = DesktopTheme.class.getResourceAsStream("/ui/fonts/BeVietnamPro-" + weight + ".ttf");
                if (stream != null) {
                    try (stream) {
                        Font.loadFont(stream, 13);
                    } catch (java.io.IOException ignored) {
                    }
                }
            }
            fontsLoaded = true;
        }
        scene.getStylesheets().add(CSS);
    }

    public static void dialog(Dialog<?> dialog, Window owner) {
        if (dialog.getOwner() == null) {
            dialog.initOwner(owner);
        }
        dialog.getDialogPane().getStylesheets().add(CSS);
        dialog.getDialogPane().setMinWidth(440);
    }

    public static void fitOnScreen(Stage stage, double width, double height) {
        Window owner = stage.getOwner();
        var screens = owner == null ? java.util.List.of(Screen.getPrimary())
                : Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight());
        Rectangle2D bounds = (screens.isEmpty() ? Screen.getPrimary() : screens.get(0)).getVisualBounds();
        double availableWidth = Math.max(1, bounds.getWidth() - 24);
        double availableHeight = Math.max(1, bounds.getHeight() - 24);
        stage.setMinWidth(Math.min(stage.getMinWidth(), availableWidth));
        stage.setMinHeight(Math.min(stage.getMinHeight(), availableHeight));
        stage.setWidth(Math.min(Math.max(stage.getMinWidth(), width), availableWidth));
        stage.setHeight(Math.min(Math.max(stage.getMinHeight(), height), availableHeight));
        stage.setX(bounds.getMinX() + (bounds.getWidth() - stage.getWidth()) / 2);
        stage.setY(bounds.getMinY() + (bounds.getHeight() - stage.getHeight()) / 2);
    }

    public static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll(styleClass.split(" "));
        label.setWrapText(true);
        return label;
    }

    public static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    public static SVGPath gavel(double size, String color) {
        SVGPath icon = new SVGPath();
        icon.setContent("M6 2 L8 0 L18 10 L16 12 Z M4 4 L6 2 L16 12 L14 14 Z "
                + "M9 10 L12 7 L23 18 L20 21 Z M1 20 L14 20 L14 23 L1 23 Z");
        icon.setFill(Color.web(color));
        icon.setScaleX(size / 24);
        icon.setScaleY(size / 24);
        return icon;
    }

    public static HBox brand(String suffix) {
        Label name = label("AUCTION", "brand");
        Label badge = label(suffix, "brand-badge");
        HBox brand = new HBox(10, gavel(25, "#fb7956"), name, badge);
        brand.setAlignment(Pos.CENTER_LEFT);
        return brand;
    }

    public static Image icon() {
        return new Image(DesktopTheme.class.getResourceAsStream("/ui/auction.png"));
    }

    public static void reveal(Node node) {
        FadeTransition fade = new FadeTransition(Duration.millis(450), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(450), node);
        slide.setFromY(14);
        slide.setToY(0);
        new ParallelTransition(fade, slide).play();
    }
}
