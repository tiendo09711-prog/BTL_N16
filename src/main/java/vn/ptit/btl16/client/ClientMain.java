package vn.ptit.btl16.client;

import vn.ptit.btl16.client.controller.ClientController;
import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.network.NetworkClient;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.client.view.MainFrame;
import vn.ptit.btl16.common.config.ClientConfig;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Swing desktop client entry point. */
public final class ClientMain {
    private ClientMain() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Default look-and-feel is acceptable for the assignment.
            }

            ClientConfig config = ClientConfig.loadDefault();
            NetworkClient network = new NetworkClient(
                    config.getMaxFrameBytes(),
                    config.getConnectTimeoutMillis(),
                    config.getRequestTimeoutMillis());
            ClientAppModel model = new ClientAppModel();
            MainFrame view = new MainFrame();
            AccountApi accounts = new AccountApi(network);
            AuctionApi auctions = new AuctionApi(network);
            ClientController controller = new ClientController(
                    config,
                    model,
                    view,
                    network,
                    accounts,
                    auctions);
            Runtime.getRuntime().addShutdownHook(new Thread(controller::close, "client-shutdown"));
            view.setVisible(true);
            controller.start();
        });
    }
}
