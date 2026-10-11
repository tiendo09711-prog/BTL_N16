package vn.ptit.btl16.common.util;

import java.net.URI;
import java.util.Locale;

public final class ConnectionCode {
    private ConnectionCode() {
    }

    public static String encode(String endpoint) {
        URI uri = URI.create(endpoint);
        if (!"ws".equalsIgnoreCase(uri.getScheme()) || !"/ws".equals(uri.getPath())
                || uri.getPort() < 1) {
            return endpoint;
        }
        String[] octets = uri.getHost().split("\\.");
        if (octets.length != 4) {
            return endpoint;
        }
        StringBuilder code = new StringBuilder("AUC-");
        for (String octet : octets) {
            int value = Integer.parseInt(octet);
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException("Invalid IPv4 address");
            }
            code.append(String.format(Locale.ROOT, "%02X", value));
        }
        return code.append('-').append(uri.getPort()).toString();
    }

    public static String decode(String value) {
        String text = value == null ? "" : value.trim();
        if (!text.toUpperCase(Locale.ROOT).startsWith("AUC-")) {
            return text;
        }
        if (!text.matches("(?i)AUC-[0-9a-f]{8}-[0-9]{1,5}")) {
            throw new IllegalArgumentException("Ma ket noi khong hop le");
        }
        int port = Integer.parseInt(text.substring(13));
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Cong ket noi khong hop le");
        }
        StringBuilder host = new StringBuilder();
        for (int offset = 4; offset < 12; offset += 2) {
            if (offset > 4) {
                host.append('.');
            }
            host.append(Integer.parseInt(text.substring(offset, offset + 2), 16));
        }
        return "ws://" + host + ':' + port + "/ws";
    }
}
