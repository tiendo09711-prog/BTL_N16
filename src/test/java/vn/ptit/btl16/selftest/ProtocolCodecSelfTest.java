package vn.ptit.btl16.selftest;

import vn.ptit.btl16.common.protocol.LengthPrefixedMessageCodec;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.WireMessage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProtocolCodecSelfTest {
    private ProtocolCodecSelfTest() {
    }

    public static void run() throws Exception {
        LengthPrefixedMessageCodec codec = new LengthPrefixedMessageCodec(2_097_152);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("username", "do_tien");
        data.put("unicode", "Dau gia thoi gian thuc - \u0110\u1ea5u gi\u00e1");
        WireMessage first = WireMessage.request(MessageType.LOGIN, "req-1", data);
        WireMessage second = WireMessage.request(MessageType.PING, "req-2", Map.of());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            codec.write(output, first);
            codec.write(output, second);
        }

        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            WireMessage readFirst = codec.read(input);
            WireMessage readSecond = codec.read(input);
            TestSupport.equals(MessageType.LOGIN, readFirst.getType(), "first type");
            TestSupport.equals("req-1", readFirst.getRequestId(), "first requestId");
            TestSupport.equals(data.get("unicode"), readFirst.get("unicode"), "UTF-8 payload");
            TestSupport.equals(MessageType.PING, readSecond.getType(), "second type");
            TestSupport.equals("req-2", readSecond.getRequestId(), "second requestId");
        }
        System.out.println("[PASS] ProtocolCodecSelfTest");
    }
}
