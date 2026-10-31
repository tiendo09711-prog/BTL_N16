package vn.ptit.btl16.client.view;

import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.model.ClientAuction;
import vn.ptit.btl16.client.service.ClientMessages;
import vn.ptit.btl16.common.util.Money;
import vn.ptit.btl16.common.util.Times;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.time.Instant;
import java.util.List;

public final class AuctionPanel extends JPanel {
    private final AuctionTableModel auctionModel = new AuctionTableModel();
    private final BidTableModel bidModel = new BidTableModel();
    private final JTable auctionTable = new JTable(auctionModel);
    private final JTable bidTable = new JTable(bidModel);

    private final JLabel accountLabel = new JLabel("Chưa đăng nhập");
    private final JButton refreshListButton = new JButton("Tải danh sách");
    private final JButton myProductsButton = new JButton("Sản phẩm bán");
    private final JButton myAuctionsButton = new JButton("Phòng của tôi");
    private final JButton addProductButton = new JButton("Thêm sản phẩm");
    private final JButton editProductButton = new JButton("Sửa sản phẩm");
    private final JButton deleteProductButton = new JButton("Xóa sản phẩm");
    private final JButton createAuctionButton = new JButton("Tạo phòng");
    private final JButton profileButton = new JButton("Hồ sơ");
    private final JButton logoutButton = new JButton("Đăng xuất");

    private final JLabel idValue = new JLabel("-");
    private final JLabel productValue = new JLabel("-");
    private final JLabel priceValue = new JLabel("-");
    private final JLabel minIncrementValue = new JLabel("-");
    private final JLabel hostValue = new JLabel("-");
    private final JLabel leaderValue = new JLabel("-");
    private final JLabel countdownValue = new JLabel("--:--");
    private final JLabel statusValue = new JLabel("-");
    private final JLabel watcherValue = new JLabel("0");
    private final JTextArea descriptionArea = new JTextArea(4, 34);
    private final JButton joinButton = new JButton("Vào phòng");
    private final JButton leaveButton = new JButton("Rời phòng");
    private final JButton historyButton = new JButton("Tải lịch sử ra giá");
    private final JTextField amountField = new JTextField(16);
    private final JButton bidButton = new JButton("Đặt giá");
    private final JButton extendButton = new JButton("Gia hạn");
    private final JButton endButton = new JButton("Kết thúc");
    private final JButton cancelButton = new JButton("Hủy phòng");
    private final JButton kickButton = new JButton("Mời người dùng");
    private final JTextArea notifications = new JTextArea(7, 40);

    private ClientAuction joinedAuction;
    private long currentUserId;
    private boolean busy;

    public AuctionPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new BorderLayout(6, 4));
        top.add(accountLabel, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.add(refreshListButton);
        top.add(actions, BorderLayout.EAST);
        JPanel managementActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        managementActions.add(myProductsButton);
        managementActions.add(myAuctionsButton);
        managementActions.add(addProductButton);
        managementActions.add(editProductButton);
        managementActions.add(deleteProductButton);
        managementActions.add(createAuctionButton);
        managementActions.add(profileButton);
        managementActions.add(logoutButton);
        JPanel topRoot = new JPanel(new BorderLayout(0, 4));
        topRoot.add(top, BorderLayout.NORTH);
        topRoot.add(managementActions, BorderLayout.SOUTH);
        add(topRoot, BorderLayout.NORTH);

        auctionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        auctionTable.setAutoCreateRowSorter(true);
        JScrollPane auctionScroll = new JScrollPane(auctionTable);
        auctionScroll.setBorder(BorderFactory.createTitledBorder("Danh sách phiên đấu giá"));

        JPanel detail = buildDetailPanel();
        JSplitPane horizontal = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                auctionScroll,
                detail);
        horizontal.setResizeWeight(0.47);
        horizontal.setDividerLocation(560);
        add(horizontal, BorderLayout.CENTER);

        notifications.setEditable(false);
        notifications.setLineWrap(true);
        notifications.setWrapStyleWord(true);
        JScrollPane notificationScroll = new JScrollPane(notifications);
        notificationScroll.setBorder(BorderFactory.createTitledBorder("Thông báo thời gian thực"));
        add(notificationScroll, BorderLayout.SOUTH);

        auctionTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showSelectedPreview();
            }
        });
        refreshEnabledState();
    }

    private JPanel buildDetailPanel() {
        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBorder(BorderFactory.createTitledBorder("Phòng đấu giá"));

        JPanel values = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        int row = 0;
        addRow(values, c, row++, "Mã phiên:", idValue);
        addRow(values, c, row++, "Sản phẩm:", productValue);
        addRow(values, c, row++, "Chủ trì:", hostValue);
        addRow(values, c, row++, "Giá hiện tại:", priceValue);
        addRow(values, c, row++, "Bước giá tối thiểu:", minIncrementValue);
        addRow(values, c, row++, "Người dẫn đầu:", leaderValue);
        addRow(values, c, row++, "Countdown:", countdownValue);
        addRow(values, c, row++, "Trạng thái:", statusValue);
        addRow(values, c, row++, "Người theo dõi:", watcherValue);

        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        c.gridy = row++;
        c.gridx = 0;
        c.gridwidth = 2;
        values.add(new JScrollPane(descriptionArea), c);

        JPanel roomButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        roomButtons.add(joinButton);
        roomButtons.add(leaveButton);
        roomButtons.add(historyButton);
        c.gridy = row++;
        values.add(roomButtons, c);

        JPanel hostButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        hostButtons.add(extendButton);
        hostButtons.add(endButton);
        hostButtons.add(cancelButton);
        hostButtons.add(kickButton);
        c.gridy = row++;
        values.add(hostButtons, c);

        JPanel bidBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bidBar.add(new JLabel("Mức giá VND:"));
        bidBar.add(amountField);
        bidBar.add(bidButton);
        c.gridy = row;
        values.add(bidBar, c);
        root.add(values, BorderLayout.NORTH);

        JScrollPane bidScroll = new JScrollPane(bidTable);
        bidScroll.setBorder(BorderFactory.createTitledBorder("Lịch sử đặt giá gần nhất"));
        root.add(bidScroll, BorderLayout.CENTER);
        return root;
    }

    private void addRow(
            JPanel panel,
            GridBagConstraints c,
            int row,
            String label,
            java.awt.Component value) {
        c.gridy = row;
        c.gridwidth = 1;
        c.gridx = 0;
        c.weightx = 0.0;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1.0;
        panel.add(value, c);
    }

    public void renderModel(ClientAppModel model) {
        accountLabel.setText("Tài khoản: " + model.getUsername()
                + " | " + model.getDisplayName());
        currentUserId = model.getUserId();
        auctionModel.setValues(model.auctionSnapshot(), model.serverNow());
        joinedAuction = model.joinedAuction();
        bidModel.setValues(model.currentBidSnapshot());
        if (joinedAuction != null) {
            renderAuction(joinedAuction, model.serverNow());
            selectAuction(joinedAuction.getAuctionId());
        } else {
            showSelectedPreview();
        }
        refreshEnabledState();
    }

    public void updateClock(Instant serverNow) {
        auctionModel.updateClock(serverNow);
        if (joinedAuction != null) {
            countdownValue.setText(joinedAuction.isOpen()
                    ? Times.countdown(joinedAuction.getEndTime(), serverNow)
                    : "00:00");
        }
    }

    private void showSelectedPreview() {
        ClientAuction selected = selectedAuction();
        if (joinedAuction == null && selected != null) {
            renderAuction(selected, Instant.now());
        }
        refreshEnabledState();
    }

    private void renderAuction(ClientAuction value, Instant now) {
        idValue.setText(Long.toString(value.getAuctionId()));
        productValue.setText(value.getProductName() + " [" + value.getProductCode() + "]");
        hostValue.setText(value.getHostUsername() + " (#" + value.getHostUserId() + ")");
        priceValue.setText(Money.display(value.getCurrentPrice()));
        minIncrementValue.setText(Money.display(value.getMinBidIncrement()));
        leaderValue.setText(value.getCurrentWinnerUsername().isBlank()
                ? "Chưa có" : value.getCurrentWinnerUsername());
        countdownValue.setText(value.isOpen()
                ? Times.countdown(value.getEndTime(), now)
                : "00:00");
        statusValue.setText(ClientMessages.auctionStatus(value.getStatus()));
        watcherValue.setText(Integer.toString(value.getWatcherCount()));
        descriptionArea.setText(value.getDescription());
    }

    private ClientAuction selectedAuction() {
        int viewRow = auctionTable.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        int modelRow = auctionTable.convertRowIndexToModel(viewRow);
        return auctionModel.getAt(modelRow);
    }

    private void selectAuction(long auctionId) {
        for (int modelRow = 0; modelRow < auctionModel.getRowCount(); modelRow++) {
            ClientAuction value = auctionModel.getAt(modelRow);
            if (value != null && value.getAuctionId() == auctionId) {
                int viewRow = auctionTable.convertRowIndexToView(modelRow);
                auctionTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
                break;
            }
        }
    }

    public Long getSelectedAuctionId() {
        ClientAuction value = selectedAuction();
        return value == null ? null : value.getAuctionId();
    }

    public String getAmountText() { return amountField.getText(); }
    public void clearAmount() { amountField.setText(""); }

    public void appendNotification(String text) {
        notifications.append(text + System.lineSeparator());
        notifications.setCaretPosition(notifications.getDocument().getLength());
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        refreshEnabledState();
    }

    private void refreshEnabledState() {
        ClientAuction selected = selectedAuction();
        boolean hasJoined = joinedAuction != null;
        refreshListButton.setEnabled(!busy);
        myProductsButton.setEnabled(!busy);
        myAuctionsButton.setEnabled(!busy);
        addProductButton.setEnabled(!busy);
        editProductButton.setEnabled(!busy);
        deleteProductButton.setEnabled(!busy);
        createAuctionButton.setEnabled(!busy);
        profileButton.setEnabled(!busy);
        logoutButton.setEnabled(!busy);
        joinButton.setEnabled(!busy && selected != null
                && (!hasJoined || selected.getAuctionId() != joinedAuction.getAuctionId()));
        leaveButton.setEnabled(!busy && hasJoined);
        historyButton.setEnabled(!busy && hasJoined);
        boolean joinedAsHost = hasJoined && joinedAuction.isHostedBy(currentUserId);
        amountField.setEnabled(!busy && hasJoined && joinedAuction.isOpen() && !joinedAsHost);
        bidButton.setEnabled(!busy && hasJoined && joinedAuction.isOpen() && !joinedAsHost);
        boolean selectedAsHost = selected != null
                && selected.isOpen()
                && selected.isHostedBy(currentUserId);
        extendButton.setEnabled(!busy && selectedAsHost);
        endButton.setEnabled(!busy && selectedAsHost);
        cancelButton.setEnabled(!busy && selectedAsHost);
        kickButton.setEnabled(!busy && selectedAsHost && hasJoined
                && joinedAuction.getAuctionId() == selected.getAuctionId());
    }

    public void onRefreshList(ActionListener listener) { refreshListButton.addActionListener(listener); }
    public void onMyProducts(ActionListener listener) { myProductsButton.addActionListener(listener); }
    public void onMyAuctions(ActionListener listener) { myAuctionsButton.addActionListener(listener); }
    public void onAddProduct(ActionListener listener) { addProductButton.addActionListener(listener); }
    public void onEditProduct(ActionListener listener) { editProductButton.addActionListener(listener); }
    public void onDeleteProduct(ActionListener listener) { deleteProductButton.addActionListener(listener); }
    public void onCreateAuction(ActionListener listener) { createAuctionButton.addActionListener(listener); }
    public void onProfile(ActionListener listener) { profileButton.addActionListener(listener); }
    public void onLogout(ActionListener listener) { logoutButton.addActionListener(listener); }
    public void onJoin(ActionListener listener) { joinButton.addActionListener(listener); }
    public void onLeave(ActionListener listener) { leaveButton.addActionListener(listener); }
    public void onHistory(ActionListener listener) { historyButton.addActionListener(listener); }
    public void onBid(ActionListener listener) { bidButton.addActionListener(listener); }
    public void onExtend(ActionListener listener) { extendButton.addActionListener(listener); }
    public void onEnd(ActionListener listener) { endButton.addActionListener(listener); }
    public void onCancel(ActionListener listener) { cancelButton.addActionListener(listener); }
    public void onKick(ActionListener listener) { kickButton.addActionListener(listener); }
}
