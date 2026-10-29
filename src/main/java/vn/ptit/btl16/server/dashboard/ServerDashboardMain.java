package vn.ptit.btl16.server.dashboard;

import vn.ptit.btl16.common.config.ServerConfig;
import vn.ptit.btl16.server.ServerApplication;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Path;

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
            Throwable cause = exception;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            String configPath = Path.of(System.getProperty(
                    "btl16.server.config", "config/server.properties")).toAbsolutePath().toString();
            JOptionPane.showMessageDialog(
                    null,
                    "Không khởi động được Server: " + message
                            + "\nChi tiết: " + cause.getMessage()
                            + "\n\n1. Bật dịch vụ MySQL trên máy Server."
                            + "\n2. Kiểm tra db.host, db.port, db.user, db.password trong:"
                            + "\n" + configPath
                            + "\n3. Nếu cổng bị chiếm, đóng Server cũ hoặc đổi cổng trong cấu hình."
                            + "\nSau đó mở lại ứng dụng Server.",
                    "Server error",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}
