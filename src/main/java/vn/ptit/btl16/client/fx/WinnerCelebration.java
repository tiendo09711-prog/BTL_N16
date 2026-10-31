package vn.ptit.btl16.client.fx;

import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** An eight-second winner badge with surrounding fireworks, shown only for a confirmed personal win. */
public final class WinnerCelebration extends StackPane implements AutoCloseable {
    private static final String ARTWORK = java.util.Objects.requireNonNull(
            WinnerCelebration.class.getResource("/ui/winner/badge.gif")).toExternalForm();
    private static final String FIREWORKS = java.util.Objects.requireNonNull(
            WinnerCelebration.class.getResource("/ui/winner/fireworks.gif")).toExternalForm();
    private static final double ASPECT = 775.0 / 363;
    private final Set<String> shown = new HashSet<>();
    private final PauseTransition timeout = new PauseTransition(Duration.seconds(8));
    private final ImageView picture = new ImageView();
    private final ImageView fireworks = new ImageView();

    public WinnerCelebration() {
        setId("winner-celebration");
        setPickOnBounds(false);
        fireworks.setId("winner-celebration-fireworks"); fireworks.setMouseTransparent(true);
        fireworks.setPreserveRatio(false); fireworks.setSmooth(true);
        fireworks.setAccessibleText("Pháo hoa chúc mừng đấu giá thành công");
        fireworks.fitWidthProperty().bind(widthProperty().multiply(.98));
        fireworks.fitHeightProperty().bind(heightProperty().multiply(.98));
        picture.setMouseTransparent(true);
        picture.setId("winner-celebration-image"); picture.setPreserveRatio(true); picture.setSmooth(true);
        picture.setAccessibleText("Chúc mừng bạn đã đấu giá thành công!");
        StackPane frame = new StackPane(picture);
        frame.setId("winner-celebration-frame"); frame.getStyleClass().add("winner-celebration-frame"); frame.setPickOnBounds(false);
        // The badge frame occupies one third of the window area, independently of the surrounding fireworks.
        var width = Bindings.createDoubleBinding(() -> Math.min(Math.sqrt(getWidth() * getHeight() * ASPECT / 3),
                Math.min(getWidth() * .9, getHeight() * .9 * ASPECT)), widthProperty(), heightProperty());
        frame.prefWidthProperty().bind(width); frame.maxWidthProperty().bind(width); frame.minWidthProperty().bind(width);
        var height = width.divide(ASPECT);
        frame.prefHeightProperty().bind(height); frame.maxHeightProperty().bind(height); frame.minHeightProperty().bind(height);
        picture.fitWidthProperty().bind(width); picture.fitHeightProperty().bind(height);
        Button close = new Button("×"); close.setId("winner-celebration-close");
        close.setAccessibleText("Đóng GIF chúc mừng"); close.getStyleClass().add("winner-celebration-close");
        close.setOnAction(event -> dismiss()); StackPane.setAlignment(close, Pos.TOP_RIGHT);
        frame.getChildren().add(close); getChildren().addAll(fireworks, frame);
        timeout.setOnFinished(event -> dismiss()); dismiss();
    }

    public boolean announce(Map<String, String> result, long userId) {
        String id = result.getOrDefault("auctionId", "");
        String winner = result.getOrDefault("winnerId", "");
        boolean self = result.containsKey("winnerIsSelf") ? Boolean.parseBoolean(result.get("winnerIsSelf"))
                : Long.toString(userId).equals(winner);
        if (userId <= 0 || id.isBlank() || winner.isBlank() || winner.equals("0") || !self || !shown.add(id)) { return false; }
        // Both layers come from the supplied GIF and restart for every confirmed win.
        fireworks.setImage(new Image(FIREWORKS, 960, 540, true, true));
        picture.setImage(new Image(ARTWORK, 775, 363, true, true));
        setVisible(true); setManaged(true); timeout.playFromStart();
        return true;
    }

    public void dismiss() { timeout.stop(); setVisible(false); setManaged(false); picture.setImage(null); fireworks.setImage(null); }
    public void clear() { dismiss(); shown.clear(); }
    @Override public void close() { clear(); }
}
