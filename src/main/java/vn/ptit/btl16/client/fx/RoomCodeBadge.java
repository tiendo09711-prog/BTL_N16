package vn.ptit.btl16.client.fx;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import vn.ptit.btl16.common.fx.DesktopTheme;

public final class RoomCodeBadge extends HBox {
    private final Label code = DesktopTheme.label("", "room-code-value");

    public RoomCodeBadge(String id) {
        this(id, "M\u00e3 ph\u00f2ng");
    }

    public RoomCodeBadge(String id, String caption) {
        this(id, caption, null);
    }

    public RoomCodeBadge(String id, String caption, Runnable onDismiss) {
        super(9);
        setId(id);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("room-code-badge");
        Button copy = new Button("Sao ch\u00e9p");
        copy.setId(id + "-copy");
        copy.getStyleClass().add("ghost");
        copy.setOnAction(event -> {
            var content = new ClipboardContent();
            content.putString(code.getText());
            Clipboard.getSystemClipboard().setContent(content);
        });
        getChildren().setAll(new Label(caption), code, copy);
        if (onDismiss != null) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button close = new Button("", MarketplaceIcons.icon("close", 14, "#76511f"));
            close.setId(id + "-close");
            close.getStyleClass().add("ghost");
            close.setAccessibleText("\u0110\u00f3ng th\u00f4ng b\u00e1o m\u00e3 ph\u00f2ng");
            close.setOnAction(event -> onDismiss.run());
            getChildren().addAll(spacer, close);
        }
        setCode("");
    }

    public void setCode(String value) {
        code.setText(value == null ? "" : value);
        setVisible(!code.getText().isBlank());
        setManaged(isVisible());
    }
}
