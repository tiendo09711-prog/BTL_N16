package vn.ptit.btl16.server.dashboard;

import vn.ptit.btl16.common.util.Money;
import vn.ptit.btl16.common.util.Times;
import vn.ptit.btl16.server.ServerApplication;
import vn.ptit.btl16.server.ServerStats;
import vn.ptit.btl16.server.auction.model.AuctionSnapshot;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional visual monitor; the actual server logic remains in ServerApplication. */
public final class ServerDashboardFrame extends JFrame {
    private final ServerApplication application;
    private final JLabel portValue = new JLabel("-");
    private final JLabel webSocketPortValue = new JLabel("-");
    private final JLabel repositoryValue = new JLabel("-");
    private final JLabel connectionsValue = new JLabel("0");
    private final JLabel sessionsValue = new JLabel("0");
    private final JLabel roomsValue = new JLabel("0");
    private final JLabel auctionsValue = new JLabel("0");
    private final JLabel sequenceValue = new JLabel("0");
    private final AuctionServerTableModel tableModel = new AuctionServerTableModel();
    private final Timer refreshTimer;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public ServerDashboardFrame(ServerApplication application) {
        super("BTL 16 - Auction Server Dashboard");
        this.application = application;
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JPanel stats = new JPanel(new GridLayout(1, 8, 6, 4));
        stats.setBorder(BorderFactory.createTitledBorder("Trang thai server"));
        addStat(stats, "TCP port", portValue);
        addStat(stats, "WS port", webSocketPortValue);
        addStat(stats, "Repository", repositoryValue);
        addStat(stats, "Connections", connectionsValue);
        addStat(stats, "Sessions", sessionsValue);
        addStat(stats, "Rooms/Subs", roomsValue);
        addStat(stats, "Open/Ended", auctionsValue);
        addStat(stats, "Sequence", sequenceValue);
        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.add(buildAddressPanel(), BorderLayout.NORTH);
        header.add(stats, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        JTable table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder("Auction state tren server"));
        add(scroll, BorderLayout.CENTER);

        JTextArea note = new JTextArea(
                "May nay la server trung tam. Gui dia chi LAN o tren cho cac may client. "
                        + "Cho phep cong WebSocket trong Windows Firewall (Private). Client "
                        + "khong ket noi truc tiep cong MySQL cua XAMPP.\n"
                        + "DB moi khong co du lieu mau. Dang ky tai khoan tren client de bat dau.");
        note.setEditable(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);

        JButton stopButton = new JButton("Dung server");
        stopButton.addActionListener(event -> stopAndExit());
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(note, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(stopButton);
        bottom.add(actions, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                stopAndExit();
            }
        });

        refreshTimer = new Timer(500, event -> refresh());
        refreshTimer.start();
        refresh();
        setSize(1050, 690);
        setLocationRelativeTo(null);
    }

    private JPanel buildAddressPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createTitledBorder("SERVER DANG CHAY - DIA CHI CHO MAY CLIENT"));
        JComboBox<String> addresses = new JComboBox<>();
        JLabel hint = new JLabel();
        JButton copy = new JButton("Sao chep dia chi");
        JButton refresh = new JButton("Lam moi dia chi");
        Runnable reload = () -> {
            addresses.removeAllItems();
            try {
                for (String url : ServerAddresses.webSocketUrls(
                        application.getConfig(), application.getWebSocketBoundPort())) {
                    addresses.addItem(url);
                }
                hint.setText(addresses.getItemCount() == 0
                        ? "Khong co dia chi LAN IPv4. Kiem tra Wi-Fi/Ethernet va cau hinh WebSocket bindAddress."
                        : "Chon IP cung mang voi client, sao chep va dan vao ung dung Client. Khong mo bang trinh duyet.");
            } catch (Exception exception) {
                hint.setText("Khong doc duoc dia chi mang: " + exception.getMessage());
            }
            copy.setEnabled(addresses.getItemCount() > 0);
        };
        copy.addActionListener(event -> {
            String selected = (String) addresses.getSelectedItem();
            if (selected != null) {
                try {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(selected), null);
                    hint.setText("Da sao chep: " + selected + " - Gui dia chi nay cho may client.");
                } catch (IllegalStateException exception) {
                    hint.setText("Clipboard dang ban. Hay thu sao chep lai.");
                }
            }
        });
        refresh.addActionListener(event -> reload.run());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(copy);
        actions.add(refresh);
        panel.add(addresses, BorderLayout.CENTER);
        panel.add(actions, BorderLayout.EAST);
        panel.add(hint, BorderLayout.SOUTH);
        reload.run();
        return panel;
    }

    private void addStat(JPanel panel, String label, JLabel value) {
        JPanel item = new JPanel(new GridLayout(2, 1));
        item.add(new JLabel(label, JLabel.CENTER));
        value.setHorizontalAlignment(JLabel.CENTER);
        item.add(value);
        panel.add(item);
    }

    private void refresh() {
        ServerStats stats = application.stats();
        portValue.setText(Integer.toString(stats.getPort()));
        webSocketPortValue.setText(Integer.toString(stats.getWebSocketPort()));
        repositoryValue.setText(stats.getRepositoryName());
        connectionsValue.setText(stats.getActiveConnections()
                + " (TCP " + stats.getTcpConnections()
                + "/WS " + stats.getWebSocketConnections() + ")");
        sessionsValue.setText(stats.getActiveSessions() + "/" + stats.getDetachedSessions());
        roomsValue.setText(stats.getRooms() + "/" + stats.getSubscriptions());
        auctionsValue.setText(stats.getOpenAuctions() + "/" + stats.getEndedAuctions());
        sequenceValue.setText(Long.toString(stats.getServerSequence()));
        tableModel.setValues(application.auctionSnapshots());
    }

    private void stopAndExit() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        refreshTimer.stop();
        application.close();
        dispose();
        SwingUtilities.invokeLater(() -> System.exit(0));
    }

    private static final class AuctionServerTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
                "ID", "San pham", "Chu tri", "Loai phong", "Buoc gia", "Gia hien tai",
                "Dan dau", "End time", "Status", "Version"
        };
        private List<AuctionSnapshot> values = new ArrayList<>();

        private void setValues(List<AuctionSnapshot> values) {
            this.values = new ArrayList<>(values);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() { return values.size(); }

        @Override
        public int getColumnCount() { return COLUMNS.length; }

        @Override
        public String getColumnName(int column) { return COLUMNS[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            AuctionSnapshot value = values.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> value.getAuctionId();
                case 1 -> value.getProduct().getName();
                case 2 -> value.getHostUsername();
                case 3 -> value.getVisibility();
                case 4 -> Money.display(value.getMinBidIncrement());
                case 5 -> Money.display(value.getCurrentPrice());
                case 6 -> value.getCurrentWinnerUsername().isBlank()
                        ? "Chua co" : value.getCurrentWinnerUsername();
                case 7 -> Times.display(value.getEndTime());
                case 8 -> value.getStatus();
                case 9 -> value.getVersion();
                default -> "";
            };
        }
    }
}
