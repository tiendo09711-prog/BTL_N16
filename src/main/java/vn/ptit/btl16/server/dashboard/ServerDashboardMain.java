package vn.ptit.btl16.server.dashboard;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.server.ServerApplication;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Starts the server and opens a small status dashboard. */
public final class ServerDashboardMain {
    private ServerDashboardMain() {
    }

    public static void main(String[] args) {
        ServerApplication application = null;
        try {
            ServerConfig config = ServerConfig.loadDefault();
            application = ServerApplication.create(config);
            application.start();
            ServerApplication runningApplication = application;
            Runtime.getRuntime().addShutdownHook(
                    new Thread(runningApplication::close, "server-dashboard-shutdown"));
            SwingUtilities.invokeLater(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                    // Default UI is sufficient.
                }
                new ServerDashboardFrame(runningApplication).setVisible(true);
            });
        } catch (Exception exception) {
            if (application != null) {
                application.close();
            }
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            JOptionPane.showMessageDialog(
                    null,
                    "Khong khoi dong duoc server: " + message,
                    "Server error",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}
