package vn.ptit.btl16.server.fx;

import javafx.geometry.Rectangle2D;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

final class SessionImagePane extends StackPane {
    private final ImageView image = new ImageView();
    private final boolean cropped;

    SessionImagePane(boolean cropped, double height) {
        this.cropped = cropped;
        getStyleClass().add("session-image-frame");
        setMinWidth(0); setPrefHeight(height); setMinHeight(height); setMaxHeight(height);
        image.setManaged(false); image.setSmooth(true); image.setPreserveRatio(!cropped);
        image.fitWidthProperty().bind(widthProperty()); image.fitHeightProperty().bind(heightProperty());
        VBox empty = new VBox(9, ServerIcons.icon("image"), new Label("Chưa có hình ảnh"));
        empty.setAlignment(Pos.CENTER); empty.getStyleClass().add("session-image-placeholder");
        empty.visibleProperty().bind(image.imageProperty().isNull());
        getChildren().setAll(empty, image);
        Rectangle clip = new Rectangle(); clip.widthProperty().bind(widthProperty()); clip.heightProperty().bind(heightProperty());
        clip.setArcWidth(24); clip.setArcHeight(24); setClip(clip);
        widthProperty().addListener((observable, before, after) -> crop());
        heightProperty().addListener((observable, before, after) -> crop());
    }

    ImageView view() { return image; }

    void setImage(Image value) { image.setImage(value); crop(); requestLayout(); }

    private void crop() {
        Image value = image.getImage();
        if (!cropped || value == null || getWidth() <= 0 || getHeight() <= 0) { return; }
        double ratio = getWidth() / getHeight(), width = value.getWidth(), height = value.getHeight();
        if (width / height > ratio) {
            double visible = height * ratio;
            image.setViewport(new Rectangle2D((width - visible) / 2, 0, visible, height));
        } else {
            double visible = width / ratio;
            image.setViewport(new Rectangle2D(0, (height - visible) / 2, width, visible));
        }
    }

    @Override protected void layoutChildren() {
        super.layoutChildren();
        image.relocate((getWidth() - image.getBoundsInLocal().getWidth()) / 2,
                (getHeight() - image.getBoundsInLocal().getHeight()) / 2);
    }
}
