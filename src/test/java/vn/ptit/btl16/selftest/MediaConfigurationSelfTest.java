package vn.ptit.btl16.selftest;

import vn.ptit.btl16.common.config.AppProperties;
import vn.ptit.btl16.common.media.ImageFormats;
import vn.ptit.btl16.server.media.MediaConfig;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MediaConfigurationSelfTest {
    private MediaConfigurationSelfTest() { }
    public static void run() throws Exception {
        Path directory = Files.createTempDirectory("btl16-media-config-");
        Path settings = directory.resolve("media.properties");
        String previousConfig = System.getProperty("btl16.test.media.config"), previousTool = System.getProperty("btl16.ffmpeg");
        try {
            System.setProperty("btl16.test.media.config", settings.toString()); System.clearProperty("btl16.ffmpeg");
            Files.writeString(settings, "");
            var resolver = MediaConfig.class.getDeclaredMethod("resolveTool", AppProperties.class, String.class, Path.class, String.class);
            resolver.setAccessible(true);
            String suffix = System.getProperty("os.name").startsWith("Windows") ? ".exe" : "";
            Path bundled = directory.resolve("ffmpeg" + suffix); Files.write(bundled, new byte[]{0});
            TestSupport.check(bundled.toFile().setExecutable(true) || Files.isExecutable(bundled), "fixture can be executable");
            Path resolved = (Path) resolver.invoke(null, AppProperties.load("btl16.test.media.config", ""), "ffmpeg", directory, suffix);
            TestSupport.equals(bundled, resolved, "bundled tools take precedence over Homebrew and PATH");
            Path custom = directory.resolve("custom ffmpeg" + suffix);
            Files.writeString(settings, "media.ffmpeg=" + custom.toString().replace('\\', '/') + "\n");
            resolved = (Path) resolver.invoke(null, AppProperties.load("btl16.test.media.config", ""), "ffmpeg", directory, suffix);
            TestSupport.equals(custom, resolved, "explicit config is respected even when missing, not silently replaced");
            System.setProperty("btl16.ffmpeg", bundled.toString());
            resolved = (Path) resolver.invoke(null, AppProperties.load("btl16.test.media.config", ""), "ffmpeg", directory, suffix);
            TestSupport.equals(bundled, resolved, "JVM property overrides config");
            for (String extension : ImageFormats.EXTENSIONS) {
                TestSupport.check(ImageFormats.chooserPatterns().contains("*." + extension)
                        && ImageFormats.chooserPatterns().contains("*." + extension.toUpperCase(java.util.Locale.ROOT)), "case-sensitive Mac chooser: " + extension);
            }
            System.out.println("[PASS] MediaConfigurationSelfTest: bundled/config/property precedence, paths with spaces and Mac uppercase image filters");
        } finally {
            restore("btl16.test.media.config", previousConfig); restore("btl16.ffmpeg", previousTool);
        }
    }
    private static void restore(String key, String value) { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } }
}
