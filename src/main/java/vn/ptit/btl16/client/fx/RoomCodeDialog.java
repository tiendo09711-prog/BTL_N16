package vn.ptit.btl16.client.fx;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.common.fx.DesktopTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class RoomCodeDialog extends Dialog<Long> {
    private final List<TextField> digits = new ArrayList<>();
    private final Label error = new Label();
    private final Button confirm;
    private boolean busy;
    private boolean distributing;

    public RoomCodeDialog(Stage owner, AuctionApi auctions, BooleanSupplier current) {
        setTitle("Nh\u1eadp m\u00e3 ph\u00f2ng");
        getDialogPane().setId("room-code-dialog");
        getDialogPane().getStyleClass().add("room-code-dialog");
        getDialogPane().setPrefWidth(420);
        Label title = DesktopTheme.label("V\u00e0o th\u1eb3ng ph\u00f2ng \u0111\u1ea5u gi\u00e1", "detail-title");
        Label note = DesktopTheme.label("Nh\u1eadp m\u00e3 g\u1ed3m 6 ch\u1eef s\u1ed1 do ch\u1ee7 ph\u00f2ng chia s\u1ebb.", "muted");
        note.setWrapText(true);
        HBox inputs = new HBox(8);
        inputs.setAlignment(Pos.CENTER);
        for (int index = 0; index < 6; index++) {
            TextField digit = new TextField();
            digit.setId("room-code-digit-" + index);
            digit.setAccessibleText("Ch\u1eef s\u1ed1 th\u1ee9 " + (index + 1));
            digit.getStyleClass().add("room-code-digit");
            digit.setAlignment(Pos.CENTER);
            digit.setPrefColumnCount(1);
            digit.setMinWidth(0);
            digit.setPrefWidth(46);
            digit.setMaxWidth(46);
            digits.add(digit);
            inputs.getChildren().add(digit);
        }
        ButtonType submit = new ButtonType("X\u00e1c nh\u1eadn", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(submit, new ButtonType("H\u1ee7y", ButtonBar.ButtonData.CANCEL_CLOSE));
        confirm = (Button) getDialogPane().lookupButton(submit);
        confirm.setId("room-code-confirm");
        confirm.setDefaultButton(true);
        setResultConverter(button -> null);
        error.setId("room-code-error");
        error.getStyleClass().add("room-code-error");
        error.setWrapText(true);
        error.setMinHeight(36);
        error.setPrefHeight(36);
        error.setVisible(false);
        getDialogPane().setContent(new VBox(14, title, note, inputs, error));
        for (int index = 0; index < digits.size(); index++) { configureDigit(index); }
        confirm.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (busy) { return; }
            String code = digits.stream().map(TextField::getText).collect(java.util.stream.Collectors.joining());
            if (code.length() != 6) {
                showError("Vui l\u00f2ng nh\u1eadp \u0111\u1ee7 6 ch\u1eef s\u1ed1.");
                digits.stream().filter(digit -> digit.getText().isEmpty()).findFirst().ifPresent(TextField::requestFocus);
                return;
            }
            if (!current.getAsBoolean()) { close(); return; }
            setBusy(true);
            error.setVisible(false);
            auctions.findByRoomCode(code).whenComplete((response, failure) -> Platform.runLater(() -> {
                if (!isShowing()) { return; }
                if (!current.getAsBoolean()) { close(); return; }
                setBusy(false);
                if (failure != null || response == null) {
                    showError("Kh\u00f4ng th\u1ec3 k\u1ebft n\u1ed1i. Vui l\u00f2ng th\u1eed l\u1ea1i.");
                } else if (!response.isSuccess()) {
                    showError("AUCTION_NOT_FOUND".equals(response.getErrorCode())
                            ? "Ph\u00f2ng kh\u00f4ng t\u1ed3n t\u1ea1i" : response.getUserMessage());
                    digits.getFirst().requestFocus();
                    digits.getFirst().selectAll();
                } else {
                    try {
                        long auctionId = Long.parseLong(response.get("auctionId"));
                        if (auctionId <= 0) { throw new NumberFormatException(); }
                        setResult(auctionId);
                        close();
                    } catch (NumberFormatException invalid) {
                        showError("Kh\u00f4ng th\u1ec3 x\u00e1c nh\u1eadn ph\u00f2ng. Vui l\u00f2ng th\u1eed l\u1ea1i.");
                    }
                }
            }));
        });
        setOnShown(event -> Platform.runLater(() -> digits.getFirst().requestFocus()));
        DesktopTheme.dialog(this, owner);
    }

    private void configureDigit(int index) {
        TextField digit = digits.get(index);
        digit.setTextFormatter(new TextFormatter<String>(change -> {
            if (distributing || !change.isContentChange()) { return change; }
            if (!change.getText().matches("[0-9]*")) { return null; }
            if (change.getText().length() > 1) {
                String pasted = change.getText();
                if (pasted.length() <= 6) { distribute(pasted.length() == 6 ? 0 : index, pasted); }
                return null;
            }
            return change.getControlNewText().length() <= 1 ? change : null;
        }));
        digit.textProperty().addListener((observable, before, after) -> {
            error.setVisible(false);
            if (!distributing && !after.isEmpty() && index < 5) { focus(index + 1); }
        });
        digit.setOnAction(event -> { confirm.fire(); event.consume(); });
        digit.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.BACK_SPACE && digit.getText().isEmpty() && index > 0) {
                digits.get(index - 1).clear(); focus(index - 1); event.consume();
            } else if (event.getCode() == KeyCode.LEFT && index > 0) {
                focus(index - 1); event.consume();
            } else if (event.getCode() == KeyCode.RIGHT && index < 5) {
                focus(index + 1); event.consume();
            }
        });
    }

    private void distribute(int start, String text) {
        distributing = true;
        try {
            for (int offset = 0; offset < text.length() && start + offset < 6; offset++) {
                digits.get(start + offset).setText(text.substring(offset, offset + 1));
            }
        } finally { distributing = false; }
        focus(Math.min(5, start + text.length()));
    }

    private void focus(int index) { digits.get(index).requestFocus(); digits.get(index).selectAll(); }

    private void setBusy(boolean value) {
        busy = value;
        confirm.setDisable(value);
        confirm.setText(value ? "\u0110ang ki\u1ec3m tra..." : "X\u00e1c nh\u1eadn");
        for (TextField digit : digits) { digit.setDisable(value); }
    }

    private void showError(String message) { error.setText(message); error.setVisible(true); }
}
