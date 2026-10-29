import java.awt.Graphics2D;
import java.awt.AlphaComposite;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.w3c.dom.Node;

/** Converts the supplied GIF to transparent frames with editable bidder/price areas.
 * Run: java tools/PrepareBidBanner.java input.gif src/main/resources/ui/bid-leading
 */
public class PrepareBidBanner {
    private static final int WIDTH = 420, HEIGHT = 323, COLUMNS = 8;
    private record Pose(double scale, double dx, double dy, double error) { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) { throw new IllegalArgumentException("Expected input GIF and output directory"); }
        Path output = Path.of(args[1]); Files.createDirectories(output);
        var reader = ImageIO.getImageReadersByFormatName("gif").next();
        List<BufferedImage> frames = new ArrayList<>(); List<Integer> delays = new ArrayList<>();
        try (var input = ImageIO.createImageInputStream(Path.of(args[0]).toFile())) {
            reader.setInput(input);
            int count = reader.getNumImages(true);
            BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB), previous = null;
            String disposal = "none"; Rectangle last = new Rectangle();
            for (int i = 0; i < count; i++) {
                if (disposal.equals("restoreToPrevious") && previous != null) { canvas = copy(previous); }
                else if (disposal.equals("restoreToBackgroundColor")) {
                    var clear = canvas.createGraphics(); clear.setComposite(AlphaComposite.Clear);
                    clear.fillRect(last.x, last.y, last.width, last.height); clear.dispose();
                }
                BufferedImage image = reader.read(i);
                Node root = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                Node descriptor = child(root, "ImageDescriptor"), control = child(root, "GraphicControlExtension");
                last = new Rectangle(Integer.parseInt(descriptor.getAttributes().getNamedItem("imageLeftPosition").getNodeValue()),
                        Integer.parseInt(descriptor.getAttributes().getNamedItem("imageTopPosition").getNodeValue()), image.getWidth(), image.getHeight());
                disposal = control.getAttributes().getNamedItem("disposalMethod").getNodeValue();
                previous = disposal.equals("restoreToPrevious") ? copy(canvas) : null;
                var paint = canvas.createGraphics(); paint.drawImage(image, last.x, last.y, null); paint.dispose();
                frames.add(copy(canvas));
                delays.add(Math.max(20, Integer.parseInt(control.getAttributes().getNamedItem("delayTime").getNodeValue()) * 10));
            }
        } finally { reader.dispose(); }
        BufferedImage atlas = new BufferedImage(WIDTH * COLUMNS, HEIGHT * ((frames.size() + COLUMNS - 1) / COLUMNS), BufferedImage.TYPE_INT_ARGB);
        StringBuilder metadata = new StringBuilder("# User-supplied banner; text removed, original frame timing and motion preserved.\n")
                .append("width=420\nheight=323\ncolumns=8\nframes=").append(frames.size()).append('\n');
        Graphics2D graphics = atlas.createGraphics();
        for (int i = 0; i < frames.size(); i++) {
            Pose pose = match(frames.get(0), frames.get(i));
            BufferedImage clean = clean(frames.get(i), pose);
            graphics.drawImage(clean, (i % COLUMNS) * WIDTH, (i / COLUMNS) * HEIGHT, null);
            metadata.append(String.format(Locale.ROOT, "frame.%d=%d,%.4f,%.2f,%.2f%n", i, delays.get(i), pose.scale, pose.dx, pose.dy));
            if (i % 11 == 0) { ImageIO.write(clean, "png", Path.of("target", "bid-banner-clean-" + i + ".png").toFile()); }
        }
        graphics.dispose();
        ImageIO.write(atlas, "png", output.resolve("frames.png").toFile());
        Files.writeString(output.resolve("animation.properties"), metadata, StandardCharsets.UTF_8);
        System.out.println("Prepared " + frames.size() + " transparent frames at " + output);
    }

    private static BufferedImage copy(BufferedImage image) {
        var copy = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        var graphics = copy.createGraphics(); graphics.drawImage(image, 0, 0, null); graphics.dispose(); return copy;
    }

    private static Node child(Node root, String name) {
        for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeName().equals(name)) { return node; }
        }
        throw new IllegalArgumentException("Missing GIF metadata: " + name);
    }

    private static Pose match(BufferedImage reference, BufferedImage frame) {
        Pose best = new Pose(1, 0, 0, Double.MAX_VALUE);
        for (double scale = .92; scale <= 1.061; scale += .01) {
            for (int dx = -12; dx <= 12; dx += 2) {
                for (int dy = -12; dy <= 16; dy += 2) {
                    Pose candidate = score(reference, frame, scale, dx, dy);
                    if (candidate.error < best.error) { best = candidate; }
                }
            }
        }
        Pose coarse = best;
        for (double scale = coarse.scale - .01; scale <= coarse.scale + .0101; scale += .002) {
            for (double dx = coarse.dx - 2; dx <= coarse.dx + 2; dx += .5) {
                for (double dy = coarse.dy - 2; dy <= coarse.dy + 2; dy += .5) {
                    Pose candidate = score(reference, frame, scale, dx, dy);
                    if (candidate.error < best.error) { best = candidate; }
                }
            }
        }
        return best;
    }

    private static Pose score(BufferedImage reference, BufferedImage frame, double scale, double dx, double dy) {
        double error = 0;
        // Fixed lettering gives a stable anchor despite the surrounding animated sparkles.
        for (int y = 166; y <= 229; y += 3) {
            for (int x = 200; x <= 325; x += 3) {
                int a = reference.getRGB(x, y);
                int b = frame.getRGB((int) Math.round(265 + (x - 265) * scale + dx), (int) Math.round(200 + (y - 200) * scale + dy));
                for (int shift : new int[]{0, 8, 16}) { int delta = ((a >> shift) & 255) - ((b >> shift) & 255); error += delta * delta; }
            }
        }
        return new Pose(scale, dx, dy, error);
    }

    private static BufferedImage clean(BufferedImage frame, Pose pose) {
        BufferedImage result = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                double rx = 265 + (x - 265 - pose.dx) / pose.scale, ry = 200 + (y - 200 - pose.dy) / pose.scale;
                int color = frame.getRGB(x, y);
                if (rx >= 198 && rx <= 330 && ry >= 164 && ry <= 185) {
                    int left = frame.getRGB((int) Math.round(265 + (197 - 265) * pose.scale + pose.dx), y);
                    int right = frame.getRGB((int) Math.round(265 + (331 - 265) * pose.scale + pose.dx), y);
                    color = mix(left, right, (rx - 197) / 134);
                } else if (rx >= 175.5 && rx <= 354.5 && ry >= 195.5 && ry <= 234) {
                    color = mix(0xff4a240c, 0xff321403, (ry - 195.5) / 38.5);
                }
                result.setRGB(x, y, color);
            }
        }
        return result;
    }

    private static int mix(int left, int right, double amount) {
        int result = 0xff000000;
        for (int shift : new int[]{0, 8, 16}) {
            result |= (int) Math.round(((left >> shift) & 255) * (1 - amount) + ((right >> shift) & 255) * amount) << shift;
        }
        return result;
    }
}
