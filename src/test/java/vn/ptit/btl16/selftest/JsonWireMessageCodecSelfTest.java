package vn.ptit.btl16.selftest;

import vn.ptit.btl16.common.protocol.JsonWireMessageCodec;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.ProtocolException;
import vn.ptit.btl16.common.protocol.WireMessage;

import java.util.Map;

public final class JsonWireMessageCodecSelfTest {
    private JsonWireMessageCodecSelfTest() {
    }

    public static void run() throws Exception {
        JsonWireMessageCodec codec = new JsonWireMessageCodec(2_097_152);
        WireMessage original = WireMessage.request(
                MessageType.LOGIN,
                "request-json-1",
                Map.of("username", "demo", "password", "secret"));
        String json = codec.encode(original);
        WireMessage decoded = codec.decode(json);
        TestSupport.equals(original.getType(), decoded.getType(), "JSON message type");
        TestSupport.equals(original.getRequestId(), decoded.getRequestId(), "JSON request id");
        TestSupport.equals("demo", decoded.get("username"), "JSON data");
        boolean malformedRejected = false;
        try {
            codec.decode("{not-json}");
        } catch (ProtocolException expected) {
            malformedRejected = true;
        }
        TestSupport.check(malformedRejected, "malformed JSON rejected");
        System.out.println("[PASS] JsonWireMessageCodecSelfTest");
    }
}
