import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataNode;
import org.w3c.dom.Node;

/** Separates the supplied celebration GIF into a centered badge and surrounding fireworks.
 * Run: java tools/PrepareWinnerCelebration.java input.gif src/main/resources/ui/winner
 */
public class PrepareWinnerCelebration {
    private record Frame(BufferedImage image, int delay, int[] components, int badge) { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) { throw new IllegalArgumentException("Expected input GIF and output directory"); }
        Path output = Path.of(args[1]); Files.createDirectories(output);
        var reader = ImageIO.getImageReadersByFormatName("gif").next(); var frames = new ArrayList<Frame>();
        Rectangle badgeBounds = null; int width, height;
        try (var input = ImageIO.createImageInputStream(Path.of(args[0]).toFile())) {
            reader.setInput(input);
            Node screen = child(reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
            width = number(screen, "logicalScreenWidth"); height = number(screen, "logicalScreenHeight");
            BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), previous = null;
            Rectangle last = new Rectangle(); String disposal = "none";
            for (int i = 0; i < reader.getNumImages(true); i++) {
                if (disposal.equals("restoreToPrevious") && previous != null) { canvas = copy(previous); }
                else if (disposal.equals("restoreToBackgroundColor")) {
                    var clear = canvas.createGraphics(); clear.setComposite(AlphaComposite.Clear);
                    clear.fillRect(last.x, last.y, last.width, last.height); clear.dispose();
                }
                Node root = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                Node descriptor = child(root, "ImageDescriptor"), control = child(root, "GraphicControlExtension");
                BufferedImage image = reader.read(i);
                last = new Rectangle(number(descriptor, "imageLeftPosition"), number(descriptor, "imageTopPosition"), image.getWidth(), image.getHeight());
                disposal = control.getAttributes().getNamedItem("disposalMethod").getNodeValue();
                previous = disposal.equals("restoreToPrevious") ? copy(canvas) : null;
                var paint = canvas.createGraphics(); paint.drawImage(image, last.x, last.y, null); paint.dispose();
                Frame frame = split(copy(canvas), Math.max(2, number(control, "delayTime"))); frames.add(frame);
                if (frame.badge() > 0) {
                    for (int y = 0; y < height; y++) { for (int x = 0; x < width; x++) {
                        if (frame.components()[y * width + x] == frame.badge()) {
                            if (badgeBounds == null) { badgeBounds = new Rectangle(x, y, 1, 1); }
                            else { badgeBounds.add(new Rectangle(x, y, 1, 1)); }
                        }
                    } }
                }
            }
        } finally { reader.dispose(); }
        if (badgeBounds == null) { throw new IllegalArgumentException("No central badge detected"); }
        write(frames, badgeBounds, output.resolve("badge.gif"), true);
        write(frames, new Rectangle(0, 0, width, height), output.resolve("fireworks.gif"), false);
        System.out.println("Prepared " + frames.size() + " frames; badge " + badgeBounds.width + " x " + badgeBounds.height + "; fireworks " + width + " x " + height);
    }

    private static Frame split(BufferedImage image, int delay) {
        int width = image.getWidth(), height = image.getHeight(); int[] labels = new int[width * height], queue = new int[labels.length];
        int component = 0, badge = 0, largest = 5000;
        for (int start = 0; start < labels.length; start++) {
            if (labels[start] != 0 || (image.getRGB(start % width, start / width) >>> 24) == 0) { continue; }
            int id = ++component, size = 1, cursor = 0; queue[0] = start; labels[start] = id;
            boolean central = false;
            while (cursor < size) {
                int pixel = queue[cursor++], x = pixel % width, y = pixel / width;
                if (Math.abs(x - width / 2) < 20 && Math.abs(y - height / 2) < 20) { central = true; }
                for (int dy = -1; dy <= 1; dy++) { for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) { continue; }
                    int next = ny * width + nx;
                    if (labels[next] == 0 && (image.getRGB(nx, ny) >>> 24) != 0) { labels[next] = id; queue[size++] = next; }
                } }
            }
            if (central && size > largest) { largest = size; badge = id; }
        }
        return new Frame(image, delay, labels, badge);
    }

    private static void write(List<Frame> frames, Rectangle bounds, Path path, boolean badge) throws Exception {
        var writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (var output = ImageIO.createImageOutputStream(path.toFile())) {
            writer.setOutput(output); writer.prepareWriteSequence(null);
            for (int index = 0; index < frames.size(); index++) {
                Frame frame = frames.get(index); var layer = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < bounds.height; y++) { for (int x = 0; x < bounds.width; x++) {
                    int sx = x + bounds.x, sy = y + bounds.y;
                    boolean onBadge = frame.badge() > 0 && frame.components()[sy * frame.image().getWidth() + sx] == frame.badge();
                    if (onBadge == badge) { layer.setRGB(x, y, frame.image().getRGB(sx, sy)); }
                } }
                BufferedImage indexed = indexed(layer);
                var metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(indexed), writer.getDefaultWriteParam());
                var root = (IIOMetadataNode) metadata.getAsTree("javax_imageio_gif_image_1.0");
                var control = (IIOMetadataNode) child(root, "GraphicControlExtension");
                control.setAttribute("disposalMethod", "restoreToBackgroundColor"); control.setAttribute("transparentColorFlag", "TRUE");
                control.setAttribute("transparentColorIndex", "0"); control.setAttribute("delayTime", Integer.toString(frame.delay()));
                if (index == 0) {
                    var extensions = new IIOMetadataNode("ApplicationExtensions"); var loop = new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID", "NETSCAPE"); loop.setAttribute("authenticationCode", "2.0");
                    loop.setUserObject(new byte[]{1, 0, 0}); extensions.appendChild(loop); root.appendChild(extensions);
                }
                metadata.setFromTree("javax_imageio_gif_image_1.0", root); writer.writeToSequence(new IIOImage(indexed, null, metadata), writer.getDefaultWriteParam());
                if (index == 20) { ImageIO.write(layer, "png", Path.of("target", badge ? "winner-badge-layer.png" : "winner-fireworks-layer.png").toFile()); }
            }
            writer.endWriteSequence();
        } finally { writer.dispose(); }
    }

    private static BufferedImage indexed(BufferedImage image) {
        var colors = new LinkedHashMap<Integer, Integer>(); colors.put(0, 0);
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        for (int color : pixels) { if ((color >>> 24) != 0) { colors.computeIfAbsent(color, ignored -> colors.size()); } }
        if (colors.size() > 256) { throw new IllegalArgumentException("Frame has more than 255 opaque GIF colors"); }
        byte[] red = new byte[colors.size()], green = new byte[red.length], blue = new byte[red.length];
        colors.forEach((color, index) -> { red[index] = (byte) (color >> 16); green[index] = (byte) (color >> 8); blue[index] = (byte) (int) color; });
        var result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, new IndexColorModel(8, colors.size(), red, green, blue, 0));
        byte[] indices = new byte[pixels.length];
        for (int i = 0; i < pixels.length; i++) { indices[i] = (byte) ((pixels[i] >>> 24) == 0 ? 0 : (int) colors.get(pixels[i])); }
        result.getRaster().setDataElements(0, 0, image.getWidth(), image.getHeight(), indices); return result;
    }

    private static BufferedImage copy(BufferedImage image) {
        var copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D paint = copy.createGraphics(); paint.drawImage(image, 0, 0, null); paint.dispose(); return copy;
    }
    private static int number(Node node, String attribute) { return Integer.parseInt(node.getAttributes().getNamedItem(attribute).getNodeValue()); }
    private static Node child(Node root, String name) {
        for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) { if (node.getNodeName().equals(name)) { return node; } }
        throw new IllegalArgumentException("Missing GIF metadata: " + name);
    }
}
