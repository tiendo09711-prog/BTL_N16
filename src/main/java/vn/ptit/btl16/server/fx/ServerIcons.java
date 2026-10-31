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
            case "auction" -> "M5 4 L10 1 L17 8 L12 13 Z M10 11 L20 21 M3 21 H13 M5 18 H11 V21";
            case "copy" -> "M9 9 H20 V21 H9 Z M15 9 V3 H3 V15 H9";
            case "refresh" -> "M20 8 A8 8 0 1 0 20 16 M20 3 V8 H15";
            case "power" -> "M12 2 V11 M6 5 A9 9 0 1 0 18 5";
            case "back" -> "M15 5 L8 12 L15 19 M8 12 H22";
            case "arrow" -> "M4 12 H20 M14 6 L20 12 L14 18";
            case "search" -> "M10 3 A7 7 0 1 0 10 17 A7 7 0 1 0 10 3 M15 15 L22 22";
            case "image" -> "M3 3 H21 V21 H3 Z M3 17 L9 11 L14 16 L18 12 L21 15 M15 7 H16";
            case "box" -> "M3 6 L12 2 L21 6 V18 L12 22 L3 18 Z M3 6 L12 11 L21 6 M12 11 V22";
            case "user" -> "M12 3 A4 4 0 1 0 12 11 A4 4 0 1 0 12 3 M4 22 V19 A8 8 0 0 1 20 19 V22";
            case "users" -> "M9 3 A3 3 0 1 0 9 9 A3 3 0 1 0 9 3 M3 21 V17 A6 6 0 0 1 15 17 V21 M17 4 A3 3 0 0 1 17 10 M18 14 A5 5 0 0 1 22 19 V21";
            case "clock" -> "M12 3 A9 9 0 1 0 12 21 A9 9 0 1 0 12 3 M12 7 V12 L16 15";
            case "grid" -> "M3 3 H10 V10 H3 Z M14 3 H21 V10 H14 Z M3 14 H10 V21 H3 Z M14 14 H21 V21 H14 Z";
            case "list" -> "M3 5 H5 M9 5 H21 M3 12 H5 M9 12 H21 M3 19 H5 M9 19 H21";
            case "chart" -> "M3 21 H22 M6 17 V12 M12 17 V8 M18 17 V3";
            case "shield" -> "M12 2 L21 6 V12 C21 17 17 20 12 23 C7 20 3 17 3 12 V6 Z M8 12 L11 15 L16 9";
            case "expand" -> "M3 9 V3 H9 M15 3 H21 V9 M21 15 V21 H15 M9 21 H3 V15";
            case "collapse" -> "M3 9 H9 V3 M15 3 V9 H21 M21 15 H15 V21 M9 21 V15 H3";
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
