package vn.ptit.btl16.server;

import vn.ptit.btl16.common.config.ServerConfig;

/** Console entry point for the central TCP server. */
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
        System.out.println(" BTL 16 - REALTIME AUCTION TCP SERVER");
        System.out.println(" Bind: " + config.getBindAddress() + ':' + config.getPort());
        System.out.println(" Repository: MySQL/JDBC");
        System.out.println(" Database: " + config.getDbName()
                + " @ " + config.getDbHost() + ':' + config.getDbPort());
        System.out.println(" Accounts: demo/demo123, alice/alice123, bob/bob123");
        System.out.println(" Anti-sniping: last " + config.getAntiSnipingWindowSeconds()
                + "s -> +" + config.getExtensionSeconds() + "s");
        System.out.println("====================================================");
    }
}
