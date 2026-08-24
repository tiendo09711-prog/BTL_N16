package vn.ptit.btl16.common.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonWireMessageCodec {
    private final ObjectMapper mapper = new ObjectMapper();
    private final int maxMessageBytes;

    public JsonWireMessageCodec(int maxMessageBytes) {
        if (maxMessageBytes < 1024) {
            throw new IllegalArgumentException("maxMessageBytes is too small");
        }
        this.maxMessageBytes = maxMessageBytes;
    }

    public String encode(WireMessage message) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("version", message.getVersion());
        root.put("kind", message.getKind().name());
        root.put("type", message.getType().name());
        root.put("requestId", message.getRequestId());
        root.put("serverSequence", message.getServerSequence());
        root.put("sentAt", message.getSentAtEpochMillis());
        ObjectNode data = root.putObject("data");
        message.getData().forEach(data::put);
        String json = mapper.writeValueAsString(root);
        validateSize(json);
        return json;
    }

    public WireMessage decode(String json) throws IOException {
        if (json == null) {
            throw new ProtocolException("WebSocket message is null");
        }
        validateSize(json);
        try {
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new ProtocolException("WireMessage JSON must be an object");
            }
            int version = requiredInt(root, "version");
            MessageKind kind = enumValue(MessageKind.class, requiredText(root, "kind"), "kind");
            MessageType type = enumValue(MessageType.class, requiredText(root, "type"), "type");
            String requestId = optionalText(root, "requestId");
            long serverSequence = optionalLong(root, "serverSequence");
            long sentAt = optionalLong(root, "sentAt");
            JsonNode dataNode = root.get("data");
            Map<String, String> data = new LinkedHashMap<>();
            if (dataNode != null && !dataNode.isNull()) {
                if (!dataNode.isObject()) {
                    throw new ProtocolException("WireMessage data must be an object");
                }
                Iterator<Map.Entry<String, JsonNode>> fields = dataNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    if (data.size() >= ProtocolLimits.MAX_DATA_ENTRIES) {
                        throw new ProtocolException("Too many WireMessage data entries");
                    }
                    JsonNode value = field.getValue();
                    if (value != null && !value.isNull() && !value.isValueNode()) {
                        throw new ProtocolException("WireMessage data values must be scalar");
                    }
                    data.put(field.getKey(), value == null || value.isNull() ? "" : value.asText());
                }
            }
            return new WireMessage(
                    version,
                    kind,
                    type,
                    requestId,
                    serverSequence,
                    sentAt,
                    data);
        } catch (JsonProcessingException exception) {
            throw new ProtocolException("Malformed WireMessage JSON", exception);
        }
    }

    private void validateSize(String json) throws ProtocolException {
        int bytes = json.getBytes(StandardCharsets.UTF_8).length;
        if (bytes <= 0 || bytes > maxMessageBytes) {
            throw new ProtocolException("Invalid WebSocket message size: " + bytes);
        }
    }

    private int requiredInt(JsonNode root, String name) throws ProtocolException {
        JsonNode value = root.get(name);
        if (value == null || !value.canConvertToInt()) {
            throw new ProtocolException("Missing or invalid " + name);
        }
        return value.intValue();
    }

    private long optionalLong(JsonNode root, String name) throws ProtocolException {
        JsonNode value = root.get(name);
        if (value == null || value.isNull()) {
            return 0L;
        }
        if (!value.canConvertToLong()) {
            throw new ProtocolException("Invalid " + name);
        }
        return value.longValue();
    }

    private String requiredText(JsonNode root, String name) throws ProtocolException {
        String value = optionalText(root, name);
        if (value.isBlank()) {
            throw new ProtocolException("Missing " + name);
        }
        return value;
    }

    private String optionalText(JsonNode root, String name) throws ProtocolException {
        JsonNode value = root.get(name);
        if (value == null || value.isNull()) {
            return "";
        }
        if (!value.isTextual()) {
            throw new ProtocolException("Invalid " + name);
        }
        return value.textValue();
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, String label)
            throws ProtocolException {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException("Unknown message " + label + ": " + value, exception);
        }
    }
}
