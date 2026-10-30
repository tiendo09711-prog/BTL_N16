package vn.ptit.btl16.server.fx;

import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

final class ServerIcons {
    private ServerIcons() { }

    static StackPane icon(String name) {
        String path = switch (name) {
            case "overview" -> "M3 3 H9 V9 H3 Z M15 3 H21 V9 H15 Z M3 15 H9 V21 H3 Z M15 15 H21 V21 H15 Z";
            case "wallet" -> "M4 5 H19 V9 M20 9 H4 V20 H20 Z M20 12 H15 V17 H20 M16.5 14.5 H17";
            case "cash" -> "M4 20 H21 M6 16 V11 M12 16 V7 M18 16 V3";
            case "log" -> "M6 3 H18 V21 H6 Z M9 8 H15 M9 12 H15 M9 16 H13";
            case "copy" -> "M9 9 H20 V21 H9 Z M15 9 V3 H3 V15 H9";
            case "refresh" -> "M20 8 A8 8 0 1 0 20 16 M20 3 V8 H15";
            case "power" -> "M12 2 V11 M6 5 A9 9 0 1 0 18 5";
            default -> throw new IllegalArgumentException("Unknown server icon: " + name);
        };
        SVGPath drawing = new SVGPath();
        drawing.setContent(path);
        drawing.getStyleClass().add("admin-icon");
        Group shape = new Group(drawing);
        shape.setScaleX(0.7);
        shape.setScaleY(0.7);
        StackPane box = new StackPane(shape);
        box.setMinSize(20, 20);
        box.setPrefSize(20, 20);
        box.setMaxSize(20, 20);
        box.setMouseTransparent(true);
        return box;
    }
}
