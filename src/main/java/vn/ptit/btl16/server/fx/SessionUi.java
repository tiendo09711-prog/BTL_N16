package vn.ptit.btl16.server.fx;

import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import vn.ptit.btl16.common.fx.DesktopTheme;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Locale;

final class SessionUi {
    private SessionUi() { }

    static Label text(String value, String style) {
        Label label = DesktopTheme.label(value, style);
        label.setWrapText(true); label.setMinWidth(0); label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    static VBox card(Node... nodes) {
        VBox card = new VBox(16, nodes);
        card.getStyleClass().add("session-surface");
        card.setMinWidth(0); card.setMinHeight(Region.USE_PREF_SIZE);
        return card;
    }

    static HBox heading(String title, String icon) {
        StackPane symbol = new StackPane(ServerIcons.icon(icon)); symbol.getStyleClass().add("session-section-icon");
        HBox heading = new HBox(10, symbol, text(title, "session-section-title"));
        heading.setAlignment(Pos.CENTER_LEFT); return heading;
    }

    static VBox field(String label, String value) {
        return new VBox(6, text(label, "session-field-label"), text(value == null || value.isBlank()
                ? "Chưa cập nhật" : value, "session-field-value"));
    }

    static GridPane facts(Node... fields) {
        GridPane grid = new GridPane(20, 20);
        grid.setMinWidth(0);
        for (int index = 0; index < fields.length; index++) { grid.add(fields[index], index % 2, index / 2); }
        ColumnConstraints first = new ColumnConstraints(); first.setPercentWidth(50);
        ColumnConstraints second = new ColumnConstraints(); second.setPercentWidth(50);
        grid.getColumnConstraints().setAll(first, second); return grid;
    }

    static GridPane columns(Node first, Node second, double firstPercent, double breakpoint) {
        GridPane grid = new GridPane(18, 18) {
            private Boolean stacked;

            private void arrange(double width) {
                boolean narrow = (width < 0 ? getWidth() : width) < breakpoint;
                if (stacked != null && stacked == narrow) { return; }
                stacked = narrow;
                ColumnConstraints left = new ColumnConstraints(); left.setPercentWidth(narrow ? 100 : firstPercent);
                ColumnConstraints right = new ColumnConstraints(); right.setPercentWidth(100 - firstPercent);
                getColumnConstraints().setAll(narrow ? java.util.List.of(left) : java.util.List.of(left, right));
                GridPane.setColumnIndex(first, 0); GridPane.setRowIndex(first, 0);
                GridPane.setColumnIndex(second, narrow ? 0 : 1); GridPane.setRowIndex(second, narrow ? 1 : 0);
            }

            @Override public Orientation getContentBias() { return Orientation.HORIZONTAL; }

            @Override protected double computePrefHeight(double width) {
                arrange(width); return super.computePrefHeight(width);
            }

            @Override protected void layoutChildren() {
                arrange(getWidth()); super.layoutChildren();
            }
        };
        grid.setMinWidth(0); grid.setMinHeight(Region.USE_PREF_SIZE);
        grid.getChildren().setAll(first, second);
        GridPane.setHgrow(first, Priority.ALWAYS); GridPane.setHgrow(second, Priority.ALWAYS);
        GridPane.setValignment(first, javafx.geometry.VPos.TOP); GridPane.setValignment(second, javafx.geometry.VPos.TOP);
        return grid;
    }

    static Node avatar(String encoded, String name, boolean hidden, double size) {
        StackPane frame = new StackPane(); frame.getStyleClass().add("session-avatar");
        frame.setMinSize(size, size); frame.setPrefSize(size, size); frame.setMaxSize(size, size);
        frame.setClip(new Circle(size / 2, size / 2, size / 2));
        if (!hidden && encoded != null && !encoded.isBlank()) {
            try {
                Image image = new Image(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
                if (!image.isError()) {
                    ImageView view = new ImageView(image); view.setFitWidth(size); view.setFitHeight(size);
                    frame.getChildren().add(view); return frame;
                }
            } catch (IllegalArgumentException ignored) { }
        }
        String initial = hidden || name == null || name.isBlank() ? "?"
                : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
        Label label = new Label(initial); label.setStyle("-fx-font-size: " + Math.round(size * .38) + "px;");
        frame.getChildren().add(label); return frame;
    }
}
