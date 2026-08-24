package vn.ptit.btl16.client.fx;

import javafx.application.Application;
import javafx.stage.Stage;
import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.network.ClientTransport;
import vn.ptit.btl16.client.network.TcpClientTransport;
import vn.ptit.btl16.client.network.WebSocketClientTransport;
import vn.ptit.btl16.client.service.AccountApi;
import vn.ptit.btl16.client.service.AuctionApi;
import vn.ptit.btl16.common.config.ClientConfig;

public final class JavaFxClientApp extends Application {
    private FxClientController controller;

    @Override
    public void start(Stage stage) {
        ClientConfig config = ClientConfig.loadDefault();
        ClientTransport transport = createTransport(config);
        controller = new FxClientController(
                stage,
                config,
                new ClientAppModel(),
                transport,
                new AccountApi(transport),
                new AuctionApi(transport));
        controller.start();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.close();
        }
    }

    private ClientTransport createTransport(ClientConfig config) {
        if ("tcp".equalsIgnoreCase(config.getTransport())) {
            return new TcpClientTransport(
                    config.getServerHost(),
                    config.getServerPort(),
                    config.getMaxFrameBytes(),
                    config.getConnectTimeoutMillis(),
                    config.getRequestTimeoutMillis());
        }
        return new WebSocketClientTransport(
                config.getWebSocketUrl(),
                config.getMaxFrameBytes(),
                config.getConnectTimeoutMillis(),
                config.getRequestTimeoutMillis());
    }
}
