package vn.ptit.btl16.selftest;

import vn.ptit.btl16.server.media.SeekableMp4;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class SeekableMp4SelfTest {
    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        for (String kind : new String[]{"stco", "co64"}) {
            byte[] extension = box("mvex", box("trex", new byte[24]));
            byte[] index = box(kind, new byte[kind.equals("stco") ? 12 : 16]);
            ByteBuffer.wrap(index).putInt(12, 1);
            byte[] movie = box("moov", join(extension, box("trak", box("mdia", box("minf", box("stbl", index))))));
            byte[] fileType = box("ftyp", "isom".getBytes(StandardCharsets.US_ASCII));
            long offset = fileType.length + movie.length + 8L;
            int indexAt = find(movie, kind) + 12;
            if (kind.equals("stco")) { ByteBuffer.wrap(movie).putInt(indexAt, (int) offset); }
            else { ByteBuffer.wrap(movie).putLong(indexAt, offset); }
            byte[] frames = new byte[]{11, 22, 33, 44, 55};
            byte[] trailer = box("mfra", box("mfro", new byte[8]));
            byte[] original = join(fileType, movie, box("mdat", frames), trailer);
            byte[] unchanged = original.clone();
            byte[] complete = SeekableMp4.complete(original);
            TestSupport.check(Arrays.equals(unchanged, original), "MP4 normalization leaves source bytes unchanged");
            TestSupport.equals(original.length - extension.length - trailer.length, complete.length, "only fragment metadata is removed");
            TestSupport.equals(-1, find(complete, "mvex"), "HTTP playback does not see a fragment declaration");
            TestSupport.equals(-1, find(complete, "mfra"), "fragment trailer is removed");
            int location = find(complete, kind) + 12;
            long updated = kind.equals("stco") ? Integer.toUnsignedLong(ByteBuffer.wrap(complete).getInt(location))
                    : ByteBuffer.wrap(complete).getLong(location);
            TestSupport.equals(offset - extension.length, updated, "32/64-bit media offsets follow the smaller movie header");
            TestSupport.check(Arrays.equals(frames, Arrays.copyOfRange(complete, (int) updated, (int) updated + frames.length)), "encoded media bytes are unchanged");
            TestSupport.check(Arrays.equals(complete, SeekableMp4.complete(complete)), "complete MP4 normalization is idempotent");
            reject(Arrays.copyOf(original, original.length - 1));
            reject(join(original, box("moof", new byte[0])));
            reject(join(original, movie));
        }
        reject(new byte[7]);
        reject(box("mdat", new byte[4]));
        System.out.println("[PASS] SeekableMp4SelfTest: complete HTTP seek index, unchanged frames, 32/64-bit offsets, idempotence and malformed/fragmented input rejection");
    }

    private static byte[] box(String type, byte[] payload) {
        return ByteBuffer.allocate(8 + payload.length).putInt(8 + payload.length)
                .put(type.getBytes(StandardCharsets.US_ASCII)).put(payload).array();
    }

    private static byte[] join(byte[]... blocks) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        for (byte[] block : blocks) { result.writeBytes(block); }
        return result.toByteArray();
    }

    private static int find(byte[] bytes, String type) {
        byte[] marker = type.getBytes(StandardCharsets.US_ASCII);
        for (int index = 4; index <= bytes.length - 4; index++) {
            if (Arrays.equals(marker, Arrays.copyOfRange(bytes, index, index + 4))) { return index; }
        }
        return -1;
    }

    private static void reject(byte[] bytes) throws Exception {
        try { SeekableMp4.complete(bytes); throw new AssertionError("Malformed/fragmented MP4 was accepted"); }
        catch (IOException expected) { }
    }
}
