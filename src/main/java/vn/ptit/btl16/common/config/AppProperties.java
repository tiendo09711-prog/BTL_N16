package vn.ptit.btl16.common.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class AppProperties {
    private final Properties properties;
    private final Path source;

    private AppProperties(Properties properties, Path source) {
        this.properties = properties;
        this.source = source;
    }

    public static AppProperties load(String systemPropertyName, String defaultPath) {
        String configuredPath = System.getProperty(systemPropertyName, defaultPath);
        Path path = Path.of(configuredPath);
        Properties values = new Properties();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                values.load(reader);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot read config: " + path, exception);
            }
        }
        return new AppProperties(values, path);
    }

    public String get(String key, String defaultValue) {
        String value = properties.getProperty(key);
        return value == null ? defaultValue : value.trim();
    }

    public int getInt(String key, int defaultValue, int min, int max) {
        String raw = get(key, Integer.toString(defaultValue));
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) {
                throw new IllegalArgumentException(
                        key + " must be in [" + min + ", " + max + "]");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer: " + raw, exception);
        }
    }

    public long getLong(String key, long defaultValue, long min, long max) {
        String raw = get(key, Long.toString(defaultValue));
        try {
            long value = Long.parseLong(raw);
            if (value < min || value > max) {
                throw new IllegalArgumentException(
                        key + " must be in [" + min + ", " + max + "]");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a long: " + raw, exception);
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String raw = get(key, Boolean.toString(defaultValue));
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        throw new IllegalArgumentException(key + " must be true or false: " + raw);
    }

    public Path getSource() {
        return source;
    }
}
