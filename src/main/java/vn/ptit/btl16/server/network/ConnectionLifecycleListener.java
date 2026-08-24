package vn.ptit.btl16.server.network;

@FunctionalInterface
public interface ConnectionLifecycleListener {
    void onDisconnected(ServerConnection connection);
}
