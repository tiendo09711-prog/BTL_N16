package vn.ptit.btl16.client.network;

@FunctionalInterface
public interface ConnectionStateListener {
    void onStateChanged(ConnectionState state, String detail);
}
