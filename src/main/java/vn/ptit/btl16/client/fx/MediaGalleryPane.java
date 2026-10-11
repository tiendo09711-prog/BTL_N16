package vn.ptit.btl16.client.fx;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import vn.ptit.btl16.client.service.ProductMediaApi;
import vn.ptit.btl16.common.fx.DesktopTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public final class MediaGalleryPane extends BorderPane implements AutoCloseable {
    private final ProductMediaApi api;
    private final MediaImagePane imagePane = new MediaImagePane();
    private final MediaView video = new MediaView();
    private final StackPane canvas = new StackPane(imagePane, video);
    private final HBox thumbnails = new HBox(8);
    private final Label status = new Label();
    private final Slider seek = new Slider(0, 1, 0);
    private final Slider volume = new Slider(0, 1, 0);
    private final Button play = new Button("Ph\u00e1t / T\u1ea1m d\u1eebng");
    private final HBox videoControls = new HBox(10, play, seek, new Label("\u00c2m l\u01b0\u1ee3ng"), volume);
    private final List<JsonNode> items = new ArrayList<>();
    private final List<Image> thumbnailImages = new ArrayList<>();
    private Image detail;
    private MediaPlayer player;
    private int selected = -1;
    private IntConsumer onSelect = index -> { };
    private Runnable retry = () -> select(selected);
    private boolean closed;

    public MediaGalleryPane(ProductMediaApi api) {
        this.api = api; setPadding(new Insets(12)); setStyle("-fx-background-color: #eef5f4;");
        setMinSize(300, 270); canvas.setMinSize(0, 0);
        Button previous = button("\u2039 Tr\u01b0\u1edbc", () -> select(selected - 1));
        Button next = button("Sau \u203a", () -> select(selected + 1));
        Button full = button("To\u00e0n m\u00e0n h\u00ecnh", () -> { if (getScene() != null && getScene().getWindow() instanceof Stage stage) { stage.setFullScreenExitHint(""); stage.setFullScreen(!stage.isFullScreen()); } });
        HBox tools = new HBox(8, previous, next, button("-", () -> imagePane.zoom(1 / 1.25)), button("+", () -> imagePane.zoom(1.25)),
                button("V\u1eeba khung", imagePane::fit), full, button("Th\u1eed l\u1ea1i", () -> retry.run()));
        VBox top = new VBox(8, tools, status); top.setPadding(new Insets(0, 0, 10, 0)); setTop(top);
        setCenter(canvas); video.setVisible(false); video.setPreserveRatio(true); video.fitWidthProperty().bind(canvas.widthProperty()); video.fitHeightProperty().bind(canvas.heightProperty());
        ScrollPane strip = new ScrollPane(thumbnails); strip.setFitToHeight(true); strip.setPrefHeight(102); strip.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED); strip.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox bottom = new VBox(8, videoControls, strip); bottom.setPadding(new Insets(10, 0, 0, 0)); setBottom(bottom);
        HBox.setHgrow(seek, Priority.ALWAYS); volume.setPrefWidth(90); videoControls.setVisible(false); videoControls.setManaged(false);
        play.setOnAction(event -> { if (player != null) { if (player.getStatus() == MediaPlayer.Status.PLAYING) { player.pause(); } else { player.play(); } } });
        seek.setOnMouseReleased(event -> { if (player != null && player.getTotalDuration().toMillis() > 0) { player.seek(player.getTotalDuration().multiply(seek.getValue())); } });
        seek.setOnKeyReleased(event -> { if (player != null) { player.seek(player.getTotalDuration().multiply(seek.getValue())); } });
        volume.valueProperty().addListener((observable, old, value) -> { if (player != null) { player.setVolume(value.doubleValue()); player.setMute(value.doubleValue() == 0); } });
    }
    public MediaImagePane imagePane() { return imagePane; }
    public void onSelection(IntConsumer value) { onSelect = value; }
    public void onRetry(Runnable value) { retry = value; }
    public int selectedIndex() { return selected; }
    public void setStatus(String value) { status.setText(value); }
    public void setItems(List<JsonNode> values) {
        closed = false;
        for (Image image : thumbnailImages) { image.cancel(); } thumbnailImages.clear(); thumbnails.getChildren().clear(); items.clear(); items.addAll(values);
        for (int index = 0; index < items.size(); index++) {
            JsonNode item = items.get(index); int choice = index;
            Image thumbnail = new Image(api.url(item.path("thumbnail").asText()), 90, 64, true, true, true); thumbnailImages.add(thumbnail);
            ImageView image = new ImageView(thumbnail); image.setFitWidth(90); image.setFitHeight(64); image.setPreserveRatio(true);
            Button tile = new Button(item.path("kind").asText().equals("VIDEO") ? "Video" : index == 0 ? "\u0110\u1ea1i di\u1ec7n" : "\u1ea2nh " + (index + 1), image);
            tile.setContentDisplay(ContentDisplay.TOP); tile.setOnAction(event -> select(choice)); thumbnails.getChildren().add(tile);
        }
        if (items.isEmpty()) { stop(); selected = -1; imagePane.setImage(null); status.setText("Ch\u01b0a c\u00f3 media"); }
        else { select(Math.max(0, Math.min(selected, items.size() - 1))); }
    }
    public void select(int index) {
        if (items.isEmpty() || closed) { return; }
        selected = Math.floorMod(index, items.size()); stop(); JsonNode item = items.get(selected); onSelect.accept(selected);
        boolean isVideo = item.path("kind").asText().equals("VIDEO"); imagePane.setVisible(!isVideo); video.setVisible(isVideo);
        videoControls.setVisible(isVideo); videoControls.setManaged(isVideo);
        status.setText("\u0110ang t\u1ea3i " + (selected + 1) + "/" + items.size() + " \u00b7 " + item.path("name").asText());
        if (isVideo) {
            try {
                Media media = new Media(api.url(item.path("video").asText()));
                player = new MediaPlayer(media); MediaPlayer current = player; current.setAutoPlay(false); current.setMute(true); volume.setValue(0); seek.setValue(0); video.setMediaPlayer(current);
                current.setOnReady(() -> { if (player == current) { status.setText("Video s\u1eb5n s\u00e0ng \u00b7 " + (int) current.getTotalDuration().toSeconds() + " gi\u00e2y"); } });
                current.setOnError(() -> status.setText("Kh\u00f4ng ph\u00e1t \u0111\u01b0\u1ee3c video. Ch\u1ecdn Th\u1eed l\u1ea1i."));
                media.setOnError(() -> status.setText("Kh\u00f4ng t\u1ea3i \u0111\u01b0\u1ee3c video. Ki\u1ec3m tra k\u1ebft n\u1ed1i/quy\u1ec1n xem."));
                current.currentTimeProperty().addListener((observable, before, now) -> { if (!seek.isValueChanging() && current.getTotalDuration().toMillis() > 0) { seek.setValue(now.toMillis() / current.getTotalDuration().toMillis()); } });
            } catch (RuntimeException exception) { status.setText("Video ch\u01b0a s\u1eb5n s\u00e0ng. Ch\u1ecdn Th\u1eed l\u1ea1i."); }
        } else {
            detail = new Image(api.url(item.path("detail").asText()), true); Image loading = detail; imagePane.setImage(loading);
            loading.progressProperty().addListener((observable, before, progress) -> { if (detail == loading && progress.doubleValue() == 1 && !loading.isError()) { status.setText(item.path("width").asInt() + " \u00d7 " + item.path("height").asInt() + " \u00b7 Cu\u1ed9n \u0111\u1ec3 zoom, k\u00e9o \u0111\u1ec3 xem chi ti\u1ebft"); } });
            loading.errorProperty().addListener((observable, before, error) -> { if (detail == loading && error) { status.setText("Kh\u00f4ng t\u1ea3i \u0111\u01b0\u1ee3c \u1ea3nh. Ch\u1ecdn Th\u1eed l\u1ea1i."); } });
        }
    }
    private static Button button(String title, Runnable action) { Button button = new Button(title); button.setOnAction(event -> action.run()); return button; }
    private void stop() { if (detail != null) { detail.cancel(); detail = null; } if (player != null) { player.stop(); player.dispose(); player = null; video.setMediaPlayer(null); } }
    @Override public void close() { closed = true; stop(); for (Image image : thumbnailImages) { image.cancel(); } thumbnailImages.clear(); }

    public static void show(Window owner, ProductMediaApi api, long productId, long auctionId, String title) {
        Stage stage = new Stage(); stage.initOwner(owner); stage.setTitle(title); MediaGalleryPane pane = new MediaGalleryPane(api);
        Scene scene = new Scene(pane, 1020, 730); DesktopTheme.apply(scene);
        stage.setScene(scene); stage.setMinWidth(720); stage.setMinHeight(500); stage.setOnHidden(event -> pane.close());
        Runnable load = () -> {
            pane.setStatus("\u0110ang t\u1ea3i th\u01b0 vi\u1ec7n...");
            api.manifest(productId, auctionId).whenComplete((body, error) -> Platform.runLater(() -> {
                if (!stage.isShowing()) { return; }
                if (error != null) { pane.setStatus(ProductMediaApi.message(error)); }
                else { List<JsonNode> items = new ArrayList<>(); body.path("media").forEach(items::add); pane.setItems(items); }
            }));
        };
        pane.onRetry(load); stage.show(); DesktopTheme.reveal(pane); load.run();
    }
}
