package vn.ptit.btl16.client.fx;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public final class MediaImagePane extends StackPane {
    private final ImageView image = new ImageView();
    private final Rectangle selection = new Rectangle();
    private boolean cropping;
    private double anchorX, anchorY, dragX, dragY;

    public MediaImagePane() {
        setMinSize(0, 0); setPrefSize(720, 420);
        getStyleClass().add("media-image-pane");
        image.setPreserveRatio(true); image.setSmooth(true); image.fitWidthProperty().bind(widthProperty().subtract(12)); image.fitHeightProperty().bind(heightProperty().subtract(12));
        selection.setManaged(false); selection.setFill(Color.web("#d6ab62", 0.18)); selection.setStroke(Color.web("#edc986")); selection.setStrokeWidth(2); selection.setVisible(false);
        getChildren().addAll(image, selection);
        Rectangle clip = new Rectangle(); clip.widthProperty().bind(widthProperty()); clip.heightProperty().bind(heightProperty()); setClip(clip);
        setOnScroll(event -> { if (!cropping) { zoom(event.getDeltaY() > 0 ? 1.18 : 1 / 1.18); } event.consume(); });
        setOnMousePressed(event -> {
            anchorX = event.getX(); anchorY = event.getY(); dragX = image.getTranslateX(); dragY = image.getTranslateY();
            if (cropping) { selection.setX(anchorX); selection.setY(anchorY); selection.setWidth(0); selection.setHeight(0); selection.setVisible(true); }
        });
        setOnMouseDragged(event -> {
            if (cropping) { selection.setX(Math.min(anchorX, event.getX())); selection.setY(Math.min(anchorY, event.getY())); selection.setWidth(Math.abs(anchorX - event.getX())); selection.setHeight(Math.abs(anchorY - event.getY())); }
            else { image.setTranslateX(dragX + event.getX() - anchorX); image.setTranslateY(dragY + event.getY() - anchorY); }
        });
    }
    public void setImage(Image value) { image.setImage(value); fit(); }
    public Image getImage() { return image.getImage(); }
    public void zoom(double factor) { double scale = Math.max(1, Math.min(10, image.getScaleX() * factor)); image.setScaleX(scale); image.setScaleY(scale); }
    public double zoomLevel() { return image.getScaleX(); }
    public void fit() { image.setScaleX(1); image.setScaleY(1); image.setTranslateX(0); image.setTranslateY(0); }
    public void setCropping(boolean value) { cropping = value; selection.setVisible(false); fit(); }
    public Rectangle2D crop() {
        var bounds = image.getBoundsInParent();
        double left = Math.max(bounds.getMinX(), selection.getX()); double top = Math.max(bounds.getMinY(), selection.getY());
        double right = Math.min(bounds.getMaxX(), selection.getX() + selection.getWidth()); double bottom = Math.min(bounds.getMaxY(), selection.getY() + selection.getHeight());
        if (!selection.isVisible() || right - left < 3 || bottom - top < 3 || bounds.getWidth() <= 0 || bounds.getHeight() <= 0) { return null; }
        return new Rectangle2D((left - bounds.getMinX()) / bounds.getWidth(), (top - bounds.getMinY()) / bounds.getHeight(),
                (right - left) / bounds.getWidth(), (bottom - top) / bounds.getHeight());
    }
}
