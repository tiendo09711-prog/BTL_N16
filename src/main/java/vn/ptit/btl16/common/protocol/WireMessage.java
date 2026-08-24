package vn.ptit.btl16.common.protocol;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class WireMessage {
    private final int version;
    private final MessageKind kind;
    private final MessageType type;
    private final String requestId;
    private final long serverSequence;
    private final long sentAtEpochMillis;
    private final Map<String, String> data;

    public WireMessage(
            int version,
            MessageKind kind,
            MessageType type,
            String requestId,
            long serverSequence,
            long sentAtEpochMillis,
            Map<String, String> data) {
        this.version = version;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.type = Objects.requireNonNull(type, "type");
        this.requestId = requestId == null ? "" : requestId;
        this.serverSequence = serverSequence;
        this.sentAtEpochMillis = sentAtEpochMillis;

        Map<String, String> copy = new LinkedHashMap<>();
        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                String key = Objects.requireNonNull(entry.getKey(), "data key");
                copy.put(key, entry.getValue() == null ? "" : entry.getValue());
            }
        }
        this.data = Collections.unmodifiableMap(copy);
    }

    public static WireMessage request(MessageType type, String requestId, Map<String, String> data) {
        return new WireMessage(
                ProtocolLimits.CURRENT_VERSION,
                MessageKind.REQUEST,
                type,
                requestId,
                0L,
                Instant.now().toEpochMilli(),
                data);
    }

    public static WireMessage response(
            MessageType type,
            WireMessage request,
            long sequence,
            Map<String, String> data) {
        return new WireMessage(
                ProtocolLimits.CURRENT_VERSION,
                MessageKind.RESPONSE,
                type,
                request == null ? "" : request.getRequestId(),
                sequence,
                Instant.now().toEpochMilli(),
                data);
    }

    public static WireMessage event(MessageType type, long sequence, Map<String, String> data) {
        return new WireMessage(
                ProtocolLimits.CURRENT_VERSION,
                MessageKind.EVENT,
                type,
                "",
                sequence,
                Instant.now().toEpochMilli(),
                data);
    }

    public int getVersion() { return version; }
    public MessageKind getKind() { return kind; }
    public MessageType getType() { return type; }
    public String getRequestId() { return requestId; }
    public long getServerSequence() { return serverSequence; }
    public long getSentAtEpochMillis() { return sentAtEpochMillis; }
    public Map<String, String> getData() { return data; }
    public String get(String key) { return data.get(key); }
    public String getOrDefault(String key, String defaultValue) { return data.getOrDefault(key, defaultValue); }

    @Override
    public String toString() {
        return "WireMessage{" +
                "version=" + version +
                ", kind=" + kind +
                ", type=" + type +
                ", requestId='" + requestId + '\'' +
                ", serverSequence=" + serverSequence +
                ", dataKeys=" + data.keySet() +
                '}';
    }
}
