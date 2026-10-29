package vn.ptit.btl16.client.fx;

import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Small vector icons stay crisp at every Windows display scale. */
final class MarketplaceIcons {
    private MarketplaceIcons() { }

    static StackPane icon(String name, double size, String color) {
        String path = switch (name) {
            case "search" -> "M10.5 3a7.5 7.5 0 1 0 0 15a7.5 7.5 0 1 0 0-15 M16 16l5 5";
            case "target" -> "M21 12a9 9 0 1 1-9-9 M17 12a5 5 0 1 1-5-5 M12 12l9-9 M17 3h4v4";
            case "eye" -> "M2 12c5-10 15-10 20 0c-5 10-15 10-20 0z M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8";
            case "coins" -> "M4 6a8 3 0 1 0 16 0a8 3 0 1 0-16 0 M4 6v5c0 4 16 4 16 0V6 M4 11v5c0 4 16 4 16 0v-5 M4 16v4c0 4 16 4 16 0v-4";
            case "lock" -> "M6 10h12v12H6z M8 10V6a4 4 0 0 1 8 0v4 M12 15v3";
            case "shield" -> "M12 2l9 4v6c0 5-5 9-9 11c-4-2-9-6-9-11V6z M12 3v18";
            case "play" -> "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20 M10 7l7 5l-7 5z";
            case "chart" -> "M3 22h18 M4 20v-7h3v7 M10 20V9h3v11 M16 20V4h3v16 M3 9l6-5l4 2l6-5";
            case "info" -> "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20 M12 11v7 M12 6h.01";
            case "edit" -> "M4 17l-1 5l5-1L21 8l-4-4z M14 7l4 4";
            case "image" -> "M3 3h18v18H3z M3 17l6-6l4 4l3-3l5 5 M16 6a2 2 0 1 0 0 4a2 2 0 1 0 0-4";
            case "phone" -> "M5 3l4 5l-2 3c1 3 3 5 6 6l3-2l5 4c-2 7-10 2-15-3S0 4 5 3z";
            case "mail" -> "M3 5h18v14H3z M3 5l9 8l9-8";
            case "save" -> "M3 3h15l3 3v15H3z M7 3v6h9V3 M7 21v-8h10v8";
            case "check" -> "M3 12l6 6L21 5";
            case "copy" -> "M8 8h13v14H8z M4 17H2V2h13v2";
            case "trash" -> "M3 6h18 M9 6V3h6v3 M5 6l1 16h12l1-16 M10 10v8 M14 10v8";
            case "qr" -> "M2 2h7v7H2z M15 2h7v7h-7z M2 15h7v7H2z M5 5h1 M18 5h1 M5 18h1 M12 2v4 M12 10h4v4h6v8h-5v-4h-5v4 M2 12h5 M10 10v3 M19 11h3";
            case "exchange" -> "M3 7h18 M17 3l4 4l-4 4 M21 17H3 M7 13l-4 4l4 4";
            case "warning" -> "M12 2L1 22h22z M12 9v6 M12 18h.01";
            case "grid" -> "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z";
            case "list" -> "M3 5h2 M9 5h12 M3 12h2 M9 12h12 M3 19h2 M9 19h12";
            case "users" -> "M9 3a3 3 0 1 0 0 6a3 3 0 1 0 0-6 M3 20v-4a5 5 0 0 1 12 0v4z M17 4a3 3 0 0 1 0 6 M18 13a4 4 0 0 1 3 4v3";
            case "chat" -> "M3 3h18v14H9l-6 5z M7 7h10 M7 11h7";
            case "box" -> "M3 6l9-4l9 4v12l-9 4l-9-4z M3 6l9 5l9-5 M12 11v11 M7 4l10 5v5";
            case "clock" -> "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18 M12 7v6l4 2 M9 1h6";
            case "hourglass" -> "M5 2h14 M5 22h14 M6 2v4c0 3 3 4 6 6c3 2 6 3 6 6v4 M18 2v4c0 3-3 4-6 6c-3 2-6 3-6 6v4 M8 6h8l-4 4z M8 19l4-5l4 5z";
            case "flame" -> "M13 2c2 6-3 6-1 10c2-1 3-3 3-5c6 5 7 9 4 13c-3 4-11 3-13-1c-3-5 1-8 2-10c-1 5 2 5 3 6c-1-6 3-7 2-13z";
            case "wallet" -> "M20 7H4a2 2 0 0 1 0-4h14v4 M3 5v14a2 2 0 0 0 2 2h16V7 M21 11h-6v6h6 M17 14h1";
            case "profile" -> "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20 M12 6a3 3 0 1 0 0 6a3 3 0 1 0 0-6 M5 19a7 7 0 0 1 14 0";
            case "star" -> "M12 2l3 6l7 1l-5 5l1 7l-6-3l-6 3l1-7l-5-5l7-1z";
            case "receipt" -> "M4 3l3 2l3-2l3 2l3-2l4 2v17l-4-2l-3 2l-3-2l-3 2l-3-2z M8 9h8 M8 13h8";
            case "rules" -> "M5 3h14v18H5z M8 7h8 M8 11h8 M8 15h6";
            case "bell" -> "M5 17h14l-2-3V9a5 5 0 0 0-10 0v5z M10 21h4 M12 2v2";
            case "filter" -> "M3 6h8 M17 6h4 M3 18h4 M13 18h8 M14 3a3 3 0 1 0 0 6a3 3 0 1 0 0-6 M10 15a3 3 0 1 0 0 6a3 3 0 1 0 0-6";
            case "sort" -> "M5 3v18 M2 18l3 3l3-3 M11 5h10 M11 11h7 M11 17h4";
            case "logout" -> "M9 3H4v18h5 M10 12h12 M18 8l4 4l-4 4";
            case "enter" -> "M14 3h6v18h-6 M2 12h13 M11 8l4 4l-4 4";
            case "menu" -> "M4 5h16 M4 12h16 M4 19h16";
            case "expand" -> "M3 9V3h6 M15 3h6v6 M21 15v6h-6 M9 21H3v-6 M3 3l6 6 M21 21l-6-6";
            case "left" -> "M15 5l-7 7l7 7";
            case "right" -> "M9 5l7 7l-7 7";
            case "plus" -> "M12 4v16 M4 12h16";
            case "minus" -> "M4 12h16";
            case "refresh" -> "M20 8a9 9 0 1 0 1 7 M20 2v6h-6";
            case "home" -> "M2 11l10-9l10 9 M5 9v12h14V9 M10 21v-7h4v7";
            case "calendar" -> "M4 5h16v16H4z M4 10h16 M8 2v6 M16 2v6 M8 14h2 M14 14h2 M8 17h2";
            case "trophy" -> "M7 3h10v5c0 8-10 8-10 0z M7 5H3v3c0 4 4 4 5 4 M17 5h4v3c0 4-4 4-5 4 M12 14v6 M7 22h10 M9 20h6";
            case "crown" -> "M3 6l5 4l4-7l4 7l5-4l-2 13H5z M5 22h14 M6 16h12";
            case "sparkles" -> "M12 2l2.5 6.5L21 11l-6.5 2.5L12 20l-2.5-6.5L3 11l6.5-2.5z M20 1v5 M17.5 3.5h5 M3 18v5 M.5 20.5h5";
            case "more" -> "M12 4h.01 M12 12h.01 M12 20h.01";
            case "close" -> "M5 5l14 14 M19 5L5 19";
            case "vase" -> "M8 2h8v3l-2 2v2c0 2 6 3 6 8c0 3-3 5-8 5s-8-2-8-5c0-5 6-6 6-8V7L8 5z M8 3h8 M5 16h14 M7 20h10 M9 11l6 7 M15 11l-6 7";
            case "heart" -> "M12 21L3 12C-3 4 7-2 12 6C17-2 27 4 21 12z";
            case "gavel" -> "M2 21h11v2H2z M13 3l7 7l-5 5l-7-7z M11 1l3 3l-7 7l-3-3z M19 9l3 3l-7 7l-3-3z M13 13l2-2l8 8l-3 3z";
            default -> throw new IllegalArgumentException("Unknown icon: " + name);
        };
        SVGPath shape = new SVGPath(); shape.setContent(path);
        boolean filled = name.equals("gavel") || name.equals("flame");
        shape.setFill(filled ? Color.web(color) : Color.TRANSPARENT);
        shape.setStroke(filled ? null : Color.web(color)); shape.setStrokeWidth(name.equals("more") ? 3.6 : 1.6);
        shape.setStrokeLineCap(StrokeLineCap.ROUND); shape.setStrokeLineJoin(StrokeLineJoin.ROUND);
        shape.setScaleX(size / 24); shape.setScaleY(size / 24);
        StackPane result = new StackPane(new Group(shape));
        result.setMinSize(size, size); result.setPrefSize(size, size); result.setMaxSize(size, size);
        result.setMouseTransparent(true);
        return result;
    }
}
