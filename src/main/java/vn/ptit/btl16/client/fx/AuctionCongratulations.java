package vn.ptit.btl16.client.fx;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import vn.ptit.btl16.common.fx.WalletUi;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Map;

/** Global results run sequentially, with exactly three right-to-left passes per auction. */
public final class AuctionCongratulations extends Pane implements AutoCloseable {
    private final Label text = new Label();
    private final ArrayDeque<String> pending = new ArrayDeque<>();
    private final LinkedHashSet<Long> seen = new LinkedHashSet<>();
    private final TranslateTransition motion = new TranslateTransition();
    private final double speed;
    private int passes;
    private long generation;

    public AuctionCongratulations() { this(115); }

    public AuctionCongratulations(double pixelsPerSecond) {
        speed = pixelsPerSecond;
        setId("auction-congratulations"); getStyleClass().add("auction-congratulations");
        setMinHeight(42); setPrefHeight(42); setMaxHeight(42);
        text.setId("auction-congratulations-text"); text.setMinWidth(Label.USE_PREF_SIZE);
        getChildren().add(text);
        Rectangle clip = new Rectangle(); clip.widthProperty().bind(widthProperty()); clip.heightProperty().bind(heightProperty()); setClip(clip);
        motion.setNode(text); motion.setInterpolator(Interpolator.LINEAR);
        motion.setOnFinished(event -> { if (++passes < 3) { runPass(); } else { next(); } });
        setVisible(false); setManaged(false);
    }

    public boolean announce(Map<String, String> result) {
        try {
            long room = Long.parseLong(result.getOrDefault("auctionId", "0"));
            String winner = result.getOrDefault("winnerUsername", "").strip();
            long id = Long.parseLong(result.getOrDefault("winnerId", "0"));
            BigDecimal price = new BigDecimal(result.getOrDefault("finalPrice", "0"));
            if (room <= 0 || id == 0 || winner.isBlank() || price.signum() <= 0 || !seen.add(room)) { return false; }
            while (seen.size() > 1024) { seen.remove(seen.iterator().next()); }
            String identity = winner + (id > 0 ? " (ID " + id + ")" : "");
            pending.add("Chúc mừng " + identity + " đã may mắn đấu giá thành công sản phẩm “"
                    + result.getOrDefault("productName", "") + "” với giá " + WalletUi.money(price) + "!");
            if (!isManaged()) { next(); }
            return true;
        } catch (NumberFormatException ignored) { return false; }
    }

    private void next() {
        motion.stop();
        String message = pending.poll();
        if (message == null) { setVisible(false); setManaged(false); return; }
        text.setText(message); passes = 0; setVisible(true); setManaged(true);
        long expected = generation;
        Platform.runLater(() -> { if (generation == expected && isManaged()) { applyCss(); layout(); runPass(); } });
    }

    private void runPass() {
        double width = Math.max(1, getWidth()), length = text.prefWidth(-1);
        text.resize(length, text.prefHeight(length)); text.relocate(0, (getHeight() - text.getHeight()) / 2);
        motion.setFromX(width); motion.setToX(-length);
        motion.setDuration(Duration.seconds(Math.max(.02, (width + length) / speed)));
        motion.playFromStart();
    }

    public void clear() { generation++; motion.stop(); pending.clear(); text.setText(""); setVisible(false); setManaged(false); }
    @Override public void close() { clear(); seen.clear(); }
}
