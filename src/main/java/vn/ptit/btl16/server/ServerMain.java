package vn.ptit.btl16.server;

import vn.ptit.btl16.common.config.ServerConfig;

/** Console entry point for the central TCP + WebSocket server. */
public final class ServerMain {
    private ServerMain() {
    }

    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.loadDefault();
        ServerApplication application = ServerApplication.create(config);
        Runtime.getRuntime().addShutdownHook(new Thread(application::close, "server-shutdown"));
        printBanner(config);
        try {
            application.start();
            application.awaitTermination();
        } finally {
            application.close();
        }
    }

    private static void printBanner(ServerConfig config) {
        System.out.println("====================================================");
        System.out.println(" BTL 16 - REALTIME AUCTION SERVER");
        System.out.println(" TCP: " + config.getBindAddress() + ':' + config.getPort()
                + " enabled=" + config.isTcpEnabled());
        System.out.println(" WebSocket: ws://" + config.getWebSocketBindAddress() + ':'
                + config.getWebSocketPort() + config.getWebSocketPath()
                + " enabled=" + config.isWebSocketEnabled());
        System.out.println(" Repository: MySQL/JDBC");
        System.out.println(" Database: " + config.getDbName()
                + " @ " + config.getDbHost() + ':' + config.getDbPort());
        System.out.println(" New database starts empty. Register an account in the client.");
        System.out.println(" Anti-sniping: last " + config.getAntiSnipingWindowSeconds()
                + "s -> +" + config.getExtensionSeconds() + "s");
        System.out.println("====================================================");
    }
}
