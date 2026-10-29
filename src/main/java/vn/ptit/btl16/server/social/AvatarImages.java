package vn.ptit.btl16.server.social;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public final class AvatarImages {
    private AvatarImages() { }

    public static String normalize(String encoded) {
        if (encoded.length() > 1_024_000) { throw new IllegalArgumentException("Ảnh đại diện tối đa 750 KB"); }
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length == 0 || bytes.length > 750 * 1024) { throw new IllegalArgumentException("Ảnh đại diện tối đa 750 KB"); }
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) { throw new IllegalArgumentException("Ảnh đại diện cần là ảnh JPEG hoặc PNG hợp lệ"); }
                var reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    String format = reader.getFormatName();
                    if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG")) { throw new IllegalArgumentException("Ảnh đại diện chỉ nhận JPEG và PNG"); }
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || (long) width * height > 8_000_000) { throw new IllegalArgumentException("Ảnh đại diện tối đa 8 triệu điểm ảnh"); }
                    BufferedImage source = reader.read(0);
                    BufferedImage result = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
                    var graphics = result.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 128, 128);
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        double scale = Math.min(128.0 / width, 128.0 / height);
                        int scaledWidth = Math.max(1, (int) (width * scale)), scaledHeight = Math.max(1, (int) (height * scale));
                        graphics.drawImage(source, (128 - scaledWidth) / 2, (128 - scaledHeight) / 2, scaledWidth, scaledHeight, null);
                    } finally { graphics.dispose(); }
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    if (!ImageIO.write(result, "jpeg", output) || output.size() > 32768) { throw new IllegalArgumentException("Không thể chuẩn hóa ảnh đại diện"); }
                    return Base64.getEncoder().encodeToString(output.toByteArray());
                } finally { reader.dispose(); }
            }
        } catch (java.io.IOException exception) { throw new IllegalArgumentException("Ảnh đại diện không hợp lệ", exception); }
    }
}
