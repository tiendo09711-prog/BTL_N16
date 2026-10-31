package vn.ptit.btl16.client.fx;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import vn.ptit.btl16.common.fx.DesktopTheme;

public final class RoomAnnouncementDialog extends Dialog<String> {
    public RoomAnnouncementDialog(Window owner, String body) {
        setTitle("Th\u00f4ng b\u00e1o ph\u00f2ng");
        getDialogPane().getStyleClass().addAll("room-dialog", "room-announcement-dialog");
        getDialogPane().setPrefWidth(560);
        Label title = DesktopTheme.label("Ghim th\u00f4ng b\u00e1o", "room-dialog-title");
        Label note = DesktopTheme.label("N\u1ed9i dung \u0111\u01b0\u1ee3c ghim ph\u00eda tr\u00ean cu\u1ed9c tr\u00f2 chuy\u1ec7n v\u00e0 hi\u1ec3n th\u1ecb v\u1edbi m\u1ecdi ng\u01b0\u1eddi trong ph\u00f2ng.", "muted");
        TextArea text = new TextArea(body); text.setId("room-announcement-body");
        text.setPromptText("Nh\u1eadp l\u1eddi ch\u00e0o, l\u01b0u \u00fd ho\u1eb7c quy \u0111\u1ecbnh c\u1ee7a phi\u00ean...");
        text.setWrapText(true); text.setPrefRowCount(5);
        Label counter = DesktopTheme.label("", "muted"); counter.setId("room-announcement-counter");
        Label hint = DesktopTheme.label("T\u1ed1i \u0111a 500 k\u00fd t\u1ef1", "muted");
        HBox footer = new HBox(8, hint, DesktopTheme.spacer(), counter); footer.setAlignment(Pos.CENTER_LEFT);
        getDialogPane().setContent(new VBox(14, title, note, text, footer));
        ButtonType save = new ButtonType("L\u01b0u th\u00f4ng b\u00e1o", ButtonBar.ButtonData.OK_DONE);
        ButtonType remove = new ButtonType("B\u1ecf ghim", ButtonBar.ButtonData.LEFT);
        getDialogPane().getButtonTypes().addAll(remove, new ButtonType("H\u1ee7y", ButtonBar.ButtonData.CANCEL_CLOSE), save);
        Button saveButton = (Button) getDialogPane().lookupButton(save); saveButton.setId("room-announcement-save");
        Button removeButton = (Button) getDialogPane().lookupButton(remove); removeButton.setDisable(body.isBlank()); removeButton.getStyleClass().add("ghost");
        Runnable validate = () -> {
            int length = text.getText().codePointCount(0, text.getText().length());
            counter.setText(length + " / 500");
            saveButton.setDisable(length > 500 || text.getText().isBlank());
            counter.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("invalid"), length > 500);
        };
        text.textProperty().addListener((observable, before, after) -> validate.run()); validate.run();
        setResultConverter(button -> button == save ? text.getText().strip() : button == remove ? "" : null);
        setOnShown(event -> text.requestFocus());
        DesktopTheme.dialog(this, owner);
    }
}
