package vn.ptit.btl16.server.dashboard;

import vn.ptit.btl16.common.config.ServerConfig;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ServerAddresses {
    private ServerAddresses() {
    }

    public static List<String> webSocketUrls(ServerConfig config, int boundPort)
            throws SocketException, UnknownHostException {
        if (!config.isWebSocketEnabled() || boundPort <= 0) {
            return List.of();
        }
        List<InetAddress> addresses = new ArrayList<>();
        for (NetworkInterface adapter : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (adapter.isUp() && !adapter.isLoopback()) {
                addresses.addAll(Collections.list(adapter.getInetAddresses()));
            }
        }
        return webSocketUrls(config.getWebSocketBindAddress(), boundPort,
                config.getWebSocketPath(), addresses);
    }

    public static List<String> webSocketUrls(String bindAddress, int port, String path,
                                             List<InetAddress> addresses) throws UnknownHostException {
        InetAddress bound = InetAddress.getByName(bindAddress);
        return addresses.stream()
                .filter(address -> address instanceof Inet4Address)
                .filter(address -> !address.isLoopbackAddress() && !address.isLinkLocalAddress()
                        && !address.isAnyLocalAddress())
                .filter(address -> bound.isAnyLocalAddress() || bound.equals(address))
                .sorted(Comparator.comparing((InetAddress address) -> !address.isSiteLocalAddress())
                        .thenComparing(InetAddress::getHostAddress))
                .map(address -> "ws://" + address.getHostAddress() + ':' + port + path)
                .distinct()
                .toList();
    }
}
