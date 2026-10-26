package vn.ptit.btl16.server.media;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class SeekableMp4 {
    private SeekableMp4() { }

    public static byte[] complete(byte[] source) throws IOException {
        List<Box> boxes = boxes(source, 0, source.length);
        Box movie = null;
        for (Box box : boxes) {
            if (box.type.equals("moof")) { throw new IOException("Expected a complete single-pass MP4"); }
            if (box.type.equals("moov")) {
                if (movie != null) { throw new IOException("Duplicate MP4 movie metadata"); }
                movie = box;
            }
        }
        if (movie == null) { throw new IOException("Missing MP4 movie metadata"); }
        List<Box> children = boxes(source, movie.start + movie.header, movie.end());
        int removed = children.stream().filter(box -> box.type.equals("mvex")).mapToInt(Box::size).sum();
        int trailers = boxes.stream().filter(box -> box.type.equals("mfra")).mapToInt(Box::size).sum();
        int originalEnd = movie.end();
        int mediaEnd = boxes.stream().filter(box -> box.type.equals("mdat")).mapToInt(Box::end).max().orElse(0);
        if (boxes.stream().noneMatch(box -> box.type.equals("mdat") && box.start >= originalEnd)
                || boxes.stream().anyMatch(box -> box.type.equals("mfra") && box.start < mediaEnd)) {
            throw new IOException("MP4 must contain indexed media after its metadata");
        }
        byte[] result = new byte[source.length - removed - trailers];
        int position = 0;
        int movieStart = 0;
        for (Box box : boxes) {
            if (box.type.equals("mfra")) { continue; }
            if (box == movie) {
                movieStart = position;
                System.arraycopy(source, box.start, result, position, box.header); position += box.header;
                for (Box child : children) {
                    if (!child.type.equals("mvex")) {
                        System.arraycopy(source, child.start, result, position, child.size); position += child.size;
                    }
                }
                ByteBuffer buffer = ByteBuffer.wrap(result);
                if (box.header == 16) { buffer.putLong(movieStart + 8, box.size - removed); }
                else { buffer.putInt(movieStart, box.size - removed); }
            } else {
                System.arraycopy(source, box.start, result, position, box.size); position += box.size;
            }
        }
        adjustOffsets(result, movieStart + movie.header, movieStart + movie.size - removed, removed, movie.end(), source.length);
        return result;
    }

    private static void adjustOffsets(byte[] bytes, int start, int end, int removed, int originalEnd, int originalLength) throws IOException {
        for (Box box : boxes(bytes, start, end)) {
            if (List.of("trak", "mdia", "minf", "stbl").contains(box.type)) {
                adjustOffsets(bytes, box.start + box.header, box.end(), removed, originalEnd, originalLength);
            } else if (box.type.equals("stco") || box.type.equals("co64")) {
                int entries = box.start + box.header;
                if (box.size < box.header + 8) { throw new IOException("Truncated MP4 chunk index"); }
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                long count = Integer.toUnsignedLong(buffer.getInt(entries + 4));
                int stride = box.type.equals("stco") ? 4 : 8;
                if (entries + 8L + count * stride != box.end()) { throw new IOException("Invalid MP4 chunk index length"); }
                for (int index = 0; index < count; index++) {
                    int location = entries + 8 + index * stride;
                    long offset = stride == 4 ? Integer.toUnsignedLong(buffer.getInt(location)) : buffer.getLong(location);
                    if (offset < originalEnd || offset >= originalLength) { throw new IOException("Invalid MP4 media offset"); }
                    if (stride == 4) { buffer.putInt(location, (int) (offset - removed)); }
                    else { buffer.putLong(location, offset - removed); }
                }
            }
        }
    }

    private static List<Box> boxes(byte[] bytes, int start, int end) throws IOException {
        List<Box> result = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (start < end) {
            if (end - start < 8) { throw new IOException("Truncated MP4 box"); }
            long size = Integer.toUnsignedLong(buffer.getInt(start));
            int header = 8;
            if (size == 1) {
                if (end - start < 16) { throw new IOException("Truncated large MP4 box"); }
                size = buffer.getLong(start + 8); header = 16;
            } else if (size == 0) { size = end - start; }
            if (size < header || size > end - start) { throw new IOException("Invalid MP4 box length"); }
            result.add(new Box(start, (int) size, header, new String(bytes, start + 4, 4, StandardCharsets.US_ASCII)));
            start += (int) size;
        }
        return result;
    }

    private record Box(int start, int size, int header, String type) {
        int end() { return start + size; }
    }
}
