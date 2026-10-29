package vn.ptit.btl16.client.fx;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Affine;
import javafx.util.Duration;
import vn.ptit.btl16.client.model.ClientBid;
import vn.ptit.btl16.common.fx.WalletUi;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/** User-supplied animated artwork with live text, scoped to accepted bids in one room. */
public final class BidLeadingBanner extends StackPane implements AutoCloseable {
    private static final int WIDTH = 420, HEIGHT = 323;
    private final LinkedHashSet<Long> observed = new LinkedHashSet<>();
    private final Timeline animation = new Timeline();
    private final PauseTransition timeout = new PauseTransition(Duration.seconds(5));
    private final Text bidder = new Text(), amount = new Text();
    private final ImageView picture = new ImageView(Artwork.IMAGE);
    private final Affine letteringMotion = new Affine();
    private long roomId, latestSequence;
    private long presentation;
    private BigDecimal latestAmount = BigDecimal.ZERO;

    public BidLeadingBanner() {
        setId("bid-leading-banner"); setMouseTransparent(true); setPickOnBounds(false);
        picture.setId("bid-leading-image"); picture.setFitWidth(WIDTH); picture.setFitHeight(HEIGHT); picture.setSmooth(true);
        bidder.setId("bid-leading-user"); bidder.setFill(Color.web("#4a2b15"));
        amount.setId("bid-leading-amount");
        amount.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#fff5bd")), new Stop(.4, Color.web("#ffe99d")),
                new Stop(.55, Color.web("#ffc85b")), new Stop(1, Color.web("#e59b25"))));
        amount.setStroke(Color.web("#6c3507")); amount.setStrokeWidth(.6);
        amount.setEffect(new DropShadow(1.5, 0, 1.5, Color.web("#2b0b00")));
        Group lettering = new Group(bidder, amount); lettering.getTransforms().add(letteringMotion);
        Pane artwork = new Pane(picture, lettering); artwork.setMinSize(WIDTH, HEIGHT);
        artwork.setPrefSize(WIDTH, HEIGHT); artwork.setMaxSize(WIDTH, HEIGHT);
        Group scaled = new Group(artwork);
        StackPane frame = new StackPane(scaled); frame.setId("bid-leading-frame");
        var width = Bindings.max(0, Bindings.min(WIDTH, Bindings.min(widthProperty().subtract(32).multiply(.42), heightProperty().subtract(32).multiply(.6 * WIDTH / HEIGHT))));
        frame.minWidthProperty().bind(width); frame.prefWidthProperty().bind(width); frame.maxWidthProperty().bind(width);
        var height = width.multiply((double) HEIGHT / WIDTH);
        frame.minHeightProperty().bind(height); frame.prefHeightProperty().bind(height); frame.maxHeightProperty().bind(height);
        scaled.scaleXProperty().bind(width.divide(WIDTH)); scaled.scaleYProperty().bind(height.divide(HEIGHT));
        StackPane.setAlignment(frame, Pos.CENTER);
        getChildren().add(frame);
        double elapsed = 0;
        for (int i = 0; i < Artwork.FRAMES.length; i++) {
            int index = i;
            animation.getKeyFrames().add(new KeyFrame(Duration.millis(elapsed), event -> showFrame(index)));
            elapsed += Artwork.FRAMES[i].millis();
        }
        animation.getKeyFrames().add(new KeyFrame(Duration.millis(elapsed)));
        animation.setCycleCount(Animation.INDEFINITE); timeout.setOnFinished(event -> dismiss());
        showFrame(0); dismiss();
    }

    /** Snapshots seed known bids silently; opening or resynchronizing a room never replays history. */
    public void setRoom(long room, List<ClientBid> history, BigDecimal currentPrice) {
        if (room != roomId) { clear(); roomId = room; }
        if (currentPrice != null) { latestAmount = latestAmount.max(currentPrice); }
        for (ClientBid bid : history) {
            if (bid.getAuctionId() == room && bid.getBidId() > 0) {
                remember(bid.getBidId()); latestSequence = Math.max(latestSequence, bid.getServerSequence());
                if (bid.getAmount() != null) { latestAmount = latestAmount.max(bid.getAmount()); }
            }
        }
    }

    public boolean announce(ClientBid bid, BigDecimal currentPrice) {
        if (bid == null || roomId <= 0 || bid.getAuctionId() != roomId || bid.getBidId() <= 0 || bid.getUserId() == 0
                || bid.getAmount() == null || bid.getAmount().signum() <= 0 || !remember(bid.getBidId())) { return false; }
        if (currentPrice == null || bid.getAmount().compareTo(currentPrice) < 0 || bid.getAmount().compareTo(latestAmount) <= 0
                || (bid.getServerSequence() > 0 && bid.getServerSequence() <= latestSequence)) { return false; }
        latestSequence = Math.max(latestSequence, bid.getServerSequence()); latestAmount = bid.getAmount();
        presentation++;
        // Negative IDs are the server's public anonymous participant IDs, never account IDs.
        String name = bid.getUserId() < 0 ? "Khách #" + Long.toString(bid.getUserId()).substring(1) : bid.getUsername().strip();
        if (name.isBlank()) { name = "Người tham gia"; }
        fit(bidder, name, 16, 127, 264, 175);
        fit(amount, WalletUi.money(bid.getAmount()), 32, 174, 265, 215);
        setAccessibleText(name + " đang dẫn đầu với " + WalletUi.money(bid.getAmount()));
        picture.setAccessibleText(getAccessibleText());
        setVisible(true); setManaged(true); showFrame(0); animation.playFromStart(); timeout.playFromStart();
        return true;
    }

    /** Keeps this bid visible until its narration completes; an older callback cannot hide a newer bid. */
    public Runnable holdForSpeech() {
        timeout.stop(); long expected = presentation;
        return () -> { if (expected == presentation) { dismiss(); } };
    }

    public void useDefaultDuration() { if (isVisible()) { timeout.playFromStart(); } }

    private static void fit(Text text, String value, double size, double width, double x, double y) {
        text.setX(0); text.setY(0);
        text.setText(value); font(text, size);
        if (text.getLayoutBounds().getWidth() > width) {
            font(text, size * width / text.getLayoutBounds().getWidth());
        }
        var bounds = text.getLayoutBounds();
        text.setX(x - bounds.getMinX() - bounds.getWidth() / 2); text.setY(y - bounds.getMinY() - bounds.getHeight() / 2);
    }

    private static void font(Text text, double size) {
        // An explicit local font prevents the application's inherited font size from shrinking the artwork text after layout.
        text.setStyle("-fx-font-family: 'Be Vietnam Pro'; -fx-font-weight: bold; -fx-font-size: " + size + "px;");
        text.setFont(Font.font("Be Vietnam Pro", FontWeight.BOLD, size)); text.applyCss();
    }

    private void showFrame(int index) {
        Frame frame = Artwork.FRAMES[index]; picture.setViewport(frame.viewport());
        letteringMotion.setToTransform(frame.scale(), 0, 265 * (1 - frame.scale()) + frame.dx(),
                0, frame.scale(), 200 * (1 - frame.scale()) + frame.dy());
    }

    private boolean remember(long id) {
        if (!observed.add(id)) { return false; }
        while (observed.size() > 512) { observed.remove(observed.iterator().next()); }
        return true;
    }

    public void dismiss() { presentation++; timeout.stop(); animation.stop(); setVisible(false); setManaged(false); }
    public void clearRoom(long room) { if (roomId == room) { clear(); } }
    public void clear() {
        dismiss(); roomId = 0; latestSequence = 0; latestAmount = BigDecimal.ZERO; observed.clear();
        bidder.setText(""); amount.setText(""); setAccessibleText(""); picture.setAccessibleText("");
    }
    @Override public void close() { clear(); }

    private record Frame(Rectangle2D viewport, int millis, double scale, double dx, double dy) { }

    private static final class Artwork {
        private static final Image IMAGE = new Image(Objects.requireNonNull(BidLeadingBanner.class.getResource("/ui/bid-leading/frames.png")).toExternalForm());
        private static final Frame[] FRAMES = load();
        private static Frame[] load() {
            Properties properties = new Properties();
            try (var input = Objects.requireNonNull(BidLeadingBanner.class.getResourceAsStream("/ui/bid-leading/animation.properties"))) {
                properties.load(input);
            } catch (IOException error) { throw new IllegalStateException("Cannot load bid banner animation", error); }
            int count = Integer.parseInt(properties.getProperty("frames")), columns = Integer.parseInt(properties.getProperty("columns"));
            Frame[] frames = new Frame[count];
            for (int i = 0; i < count; i++) {
                String[] values = properties.getProperty("frame." + i).split(",");
                frames[i] = new Frame(new Rectangle2D((i % columns) * WIDTH, (i / columns) * HEIGHT, WIDTH, HEIGHT),
                        Integer.parseInt(values[0]), Double.parseDouble(values[1]), Double.parseDouble(values[2]), Double.parseDouble(values[3]));
            }
            return frames;
        }
    }
}
