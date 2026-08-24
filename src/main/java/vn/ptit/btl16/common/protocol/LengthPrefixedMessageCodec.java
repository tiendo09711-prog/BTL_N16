package vn.ptit.btl16.common.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LengthPrefixedMessageCodec implements MessageCodec {
    private final int maxFrameBytes;

    public LengthPrefixedMessageCodec(int maxFrameBytes) {
        if (maxFrameBytes < 1024) {
            throw new IllegalArgumentException("maxFrameBytes is too small");
        }
        this.maxFrameBytes = maxFrameBytes;
    }

    @Override
    public WireMessage read(DataInputStream input) throws IOException {
        int frameLength;
        try {
            frameLength = input.readInt();
        } catch (EOFException eof) {
            throw eof;
        }
        if (frameLength <= 0 || frameLength > maxFrameBytes) {
            throw new ProtocolException("Invalid frame length: " + frameLength);
        }

        byte[] frame = new byte[frameLength];
        input.readFully(frame);
        try (DataInputStream payload = new DataInputStream(new ByteArrayInputStream(frame))) {
            int magic = payload.readInt();
            if (magic != ProtocolLimits.MAGIC) {
                throw new ProtocolException("Invalid protocol magic");
            }
            int version = payload.readInt();
            MessageKind kind = parseEnum(MessageKind.class, readString(payload), "message kind");
            MessageType type = parseEnum(MessageType.class, readString(payload), "message type");
            String requestId = readString(payload);
            long sequence = payload.readLong();
            long sentAt = payload.readLong();
            int count = payload.readInt();
            if (count < 0 || count > ProtocolLimits.MAX_DATA_ENTRIES) {
                throw new ProtocolException("Invalid data entry count: " + count);
            }
            Map<String, String> data = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                String key = readString(payload);
                String value = readString(payload);
                if (data.put(key, value) != null) {
                    throw new ProtocolException("Duplicate payload key: " + key);
                }
            }
            if (payload.available() != 0) {
                throw new ProtocolException("Trailing bytes in frame: " + payload.available());
            }
            return new WireMessage(version, kind, type, requestId, sequence, sentAt, data);
        }
    }

    @Override
    public void write(DataOutputStream output, WireMessage message) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream payload = new DataOutputStream(bytes)) {
            payload.writeInt(ProtocolLimits.MAGIC);
            payload.writeInt(message.getVersion());
            writeString(payload, message.getKind().name());
            writeString(payload, message.getType().name());
            writeString(payload, message.getRequestId());
            payload.writeLong(message.getServerSequence());
            payload.writeLong(message.getSentAtEpochMillis());
            payload.writeInt(message.getData().size());
            for (Map.Entry<String, String> entry : message.getData().entrySet()) {
                writeString(payload, entry.getKey());
                writeString(payload, entry.getValue());
            }
            payload.flush();
        }

        byte[] frame = bytes.toByteArray();
        if (frame.length > maxFrameBytes) {
            throw new ProtocolException("Message exceeds maxFrameBytes: " + frame.length);
        }
        output.writeInt(frame.length);
        output.write(frame);
        output.flush();
    }

    private void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > ProtocolLimits.MAX_STRING_BYTES) {
            throw new ProtocolException("String is too large: " + bytes.length);
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > ProtocolLimits.MAX_STRING_BYTES) {
            throw new ProtocolException("Invalid string length: " + length);
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String raw, String label)
            throws ProtocolException {
        try {
            return Enum.valueOf(enumType, raw);
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException("Unknown " + label + ": " + raw, exception);
        }
    }
}
