package vn.ptit.btl16.server.media;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public record MediaContent(long size, Source source) {
    @FunctionalInterface
    public interface Source {
        InputStream open(long offset) throws IOException;
    }

    public InputStream open(long offset) throws IOException {
        if (offset < 0 || offset > size) { throw new IOException("Invalid media offset"); }
        return source.open(offset);
    }

    public static MediaContent memory(byte[] bytes) {
        return new MediaContent(bytes.length, offset -> new ByteArrayInputStream(bytes, (int) offset, bytes.length - (int) offset));
    }
}
