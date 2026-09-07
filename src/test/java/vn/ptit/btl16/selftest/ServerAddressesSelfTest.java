package vn.ptit.btl16.selftest;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.server.dashboard.ServerAddresses;

import java.net.InetAddress;
import java.util.List;

public final class ServerAddressesSelfTest {
    private ServerAddressesSelfTest() {
    }

    public static void run() throws Exception {
        List<InetAddress> addresses = List.of(
                InetAddress.getByName("127.0.0.1"),
                InetAddress.getByName("169.254.1.2"),
                InetAddress.getByName("0.0.0.0"),
                InetAddress.getByName("::1"),
                InetAddress.getByName("192.168.1.18"),
                InetAddress.getByName("10.0.0.2"),
                InetAddress.getByName("192.168.1.18"));
        TestSupport.equals(List.of("ws://10.0.0.2:8890/ws", "ws://192.168.1.18:8890/ws"),
                ServerAddresses.webSocketUrls("0.0.0.0", 8890, "/ws", addresses),
                "LAN addresses exclude loopback, link-local, wildcard and duplicates");
        TestSupport.equals(List.of("ws://192.168.1.18:9090/auction"),
                ServerAddresses.webSocketUrls("192.168.1.18", 9090, "/auction", addresses),
                "advertise only bound interface with configured port and path");
        TestSupport.equals(List.of(), ServerAddresses.webSocketUrls("127.0.0.1", 8890, "/ws", addresses),
                "loopback-only listener must not advertise a LAN address");
        TestSupport.equals(List.of(), ServerAddresses.webSocketUrls("192.168.2.1", 8890, "/ws", addresses),
                "do not advertise unavailable bound address");
        TestSupport.equals(List.of(), ServerAddresses.webSocketUrls("0.0.0.0", 8890, "/ws", List.of()),
                "no LAN adapter means no shareable address");
        TestSupport.equals(List.of(), ServerAddresses.webSocketUrls(ServerConfig.forTests(0), 8890),
                "disabled WebSocket must not advertise an address");
        TestSupport.equals(List.of(), ServerAddresses.webSocketUrls(ServerConfig.forTransportTests(0, 0), 0),
                "unbound WebSocket must not advertise an address");
        System.out.println("[PASS] ServerAddressesSelfTest");
    }
}
