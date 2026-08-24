package vn.ptit.btl16.client.view;

import vn.ptit.btl16.client.model.ClientAppModel;
import vn.ptit.btl16.client.model.ClientProduct;
import vn.ptit.btl16.client.network.ConnectionState;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.WindowListener;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class MainFrame extends JFrame {
    private static final String LOGIN_CARD = "login";
    private static final String AUCTION_CARD = "auction";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final LoginPanel loginPanel = new LoginPanel();
    private final AuctionPanel auctionPanel = new AuctionPanel();
    private final JLabel stateLabel = new JLabel(ConnectionState.DISCONNECTED.name());
    private final JLabel detailLabel = new JLabel("Chua ket noi");
    private final JLabel latencyLabel = new JLabel("RTT: -");

    public MainFrame() {
        super("BTL 16 - Realtime Auction Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        cards.add(loginPanel, LOGIN_CARD);
        cards.add(auctionPanel, AUCTION_CARD);
        add(cards, BorderLayout.CENTER);

        JPanel status = new JPanel(new BorderLayout(8, 0));
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        status.add(stateLabel, BorderLayout.WEST);
        status.add(detailLabel, BorderLayout.CENTER);
        status.add(latencyLabel, BorderLayout.EAST);
        add(status, BorderLayout.SOUTH);

        setSize(1220, 760);
        setLocationRelativeTo(null);
        showLogin();
    }

    public LoginPanel getLoginPanel() { return loginPanel; }
    public AuctionPanel getAuctionPanel() { return auctionPanel; }

    public void showLogin() {
        cardLayout.show(cards, LOGIN_CARD);
    }

    public void showAuction(ClientAppModel model) {
        auctionPanel.renderModel(model);
        cardLayout.show(cards, AUCTION_CARD);
    }

    public void renderAuction(ClientAppModel model) {
        auctionPanel.renderModel(model);
    }

    public void renderConnection(ConnectionState state, String detail) {
        stateLabel.setText(state.name());
        detailLabel.setText(detail == null ? "" : detail);
        loginPanel.setConnectionAvailable(state == ConnectionState.CONNECTED);
    }

    public void setLatency(String value) {
        latencyLabel.setText(value == null || value.isBlank() ? "RTT: -" : "RTT: " + value + " ms");
    }

    public void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "Thong bao", JOptionPane.INFORMATION_MESSAGE);
    }

    public void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Loi", JOptionPane.ERROR_MESSAGE);
    }

    public boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(
                this,
                message,
                "Xac nhan",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    public Optional<ProductInput> promptProduct(ClientProduct current) {
        JTextField code = new JTextField(current == null ? "" : current.getCode());
        JTextField name = new JTextField(current == null ? "" : current.getName());
        JTextArea description = new JTextArea(
                current == null ? "" : current.getDescription(), 5, 28);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        JPanel panel = formPanel(
                "Ma san pham", code,
                "Ten san pham", name,
                "Mo ta", new javax.swing.JScrollPane(description));
        int option = JOptionPane.showConfirmDialog(
                this,
                panel,
                current == null ? "Them san pham" : "Sua san pham",
                JOptionPane.OK_CANCEL_OPTION);
        if (option != JOptionPane.OK_OPTION) {
            return Optional.empty();
        }
        return Optional.of(new ProductInput(
                current == null ? 0L : current.getProductId(),
                code.getText(),
                name.getText(),
                description.getText()));
    }

    public Optional<ClientProduct> chooseProduct(
            List<ClientProduct> products,
            String title,
            boolean activeOnly) {
        List<ClientProduct> choices = products.stream()
                .filter(value -> !activeOnly || value.isActive())
                .toList();
        if (choices.isEmpty()) {
            showInfo(activeOnly
                    ? "Ban chua co san pham dang hoat dong"
                    : "Ban chua co san pham");
            return Optional.empty();
        }
        JComboBox<ClientProduct> combo = new JComboBox<>(choices.toArray(ClientProduct[]::new));
        int option = JOptionPane.showConfirmDialog(
                this,
                combo,
                title,
                JOptionPane.OK_CANCEL_OPTION);
        return option == JOptionPane.OK_OPTION
                ? Optional.ofNullable((ClientProduct) combo.getSelectedItem())
                : Optional.empty();
    }

    public Optional<AuctionInput> promptAuction(List<ClientProduct> products) {
        List<ClientProduct> active = products.stream().filter(ClientProduct::isActive).toList();
        if (active.isEmpty()) {
            showInfo("Can co it nhat mot san pham dang hoat dong de tao phong");
            return Optional.empty();
        }
        JComboBox<ClientProduct> product = new JComboBox<>(active.toArray(ClientProduct[]::new));
        JTextField startPrice = new JTextField("1000000");
        JTextField minIncrement = new JTextField("50000");
        JTextField duration = new JTextField("10");
        JPanel panel = formPanel(
                "San pham", product,
                "Gia khoi diem", startPrice,
                "Buoc gia toi thieu", minIncrement,
                "Thoi luong (phut)", duration);
        int option = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Tao phong dau gia",
                JOptionPane.OK_CANCEL_OPTION);
        if (option != JOptionPane.OK_OPTION) {
            return Optional.empty();
        }
        return Optional.of(new AuctionInput(
                (ClientProduct) product.getSelectedItem(),
                startPrice.getText(),
                minIncrement.getText(),
                duration.getText()));
    }

    public Optional<String> promptText(String title, String message, String initialValue) {
        String value = (String) JOptionPane.showInputDialog(
                this,
                message,
                title,
                JOptionPane.QUESTION_MESSAGE,
                null,
                null,
                initialValue == null ? "" : initialValue);
        return value == null ? Optional.empty() : Optional.of(value.trim());
    }

    public void showProducts(List<ClientProduct> products) {
        String text = products.isEmpty()
                ? "Ban chua co san pham"
                : products.stream()
                        .map(value -> "#" + value.getProductId() + " - " + value
                                + " | " + (value.isActive() ? "ACTIVE" : "INACTIVE"))
                        .collect(Collectors.joining(System.lineSeparator()));
        JTextArea area = new JTextArea(text, 12, 48);
        area.setEditable(false);
        JOptionPane.showMessageDialog(
                this,
                new javax.swing.JScrollPane(area),
                "San pham cua toi",
                JOptionPane.INFORMATION_MESSAGE);
    }

    public Optional<RegistrationInput> promptRegistration() {
        JTextField username = new JTextField();
        JPasswordField password = new JPasswordField();
        JPasswordField confirm = new JPasswordField();
        JTextField displayName = new JTextField();
        JTextField email = new JTextField();
        JTextField phone = new JTextField();
        JPanel panel = formPanel(
                "Username", username,
                "Mat khau", password,
                "Nhap lai", confirm,
                "Ten hien thi", displayName,
                "Email", email,
                "So dien thoai", phone);
        int option = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Dang ky tai khoan",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return Optional.empty();
        }
        return Optional.of(new RegistrationInput(
                username.getText(),
                password.getPassword(),
                confirm.getPassword(),
                displayName.getText(),
                email.getText(),
                phone.getText()));
    }

    public Optional<ProfileInput> promptProfile(ClientAppModel model) {
        JTextField displayName = new JTextField(model.getDisplayName());
        JTextField email = new JTextField(model.getEmail());
        JTextField phone = new JTextField(model.getPhone());
        JPanel panel = formPanel(
                "Username", new JLabel(model.getUsername()),
                "Ten hien thi", displayName,
                "Email", email,
                "So dien thoai", phone,
                "Tao luc", new JLabel(model.getCreatedAt()),
                "Login gan nhat", new JLabel(model.getLastLoginAt()));
        int option = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Ho so tai khoan",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return Optional.empty();
        }
        return Optional.of(new ProfileInput(
                displayName.getText(),
                email.getText(),
                phone.getText()));
    }

    public Optional<PasswordChangeInput> promptPasswordChange() {
        JPasswordField current = new JPasswordField();
        JPasswordField next = new JPasswordField();
        JPasswordField confirm = new JPasswordField();
        JPanel panel = formPanel(
                "Mat khau hien tai", current,
                "Mat khau moi", next,
                "Nhap lai", confirm);
        int option = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Doi mat khau",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return Optional.empty();
        }
        return Optional.of(new PasswordChangeInput(
                current.getPassword(),
                next.getPassword(),
                confirm.getPassword()));
    }

    public int promptProfileAction(ClientAppModel model) {
        Object[] options = {"Cap nhat ho so", "Doi mat khau", "Dong"};
        return JOptionPane.showOptionDialog(
                this,
                "Tai khoan: " + model.getUsername() + "\nTen: " + model.getDisplayName(),
                "Ho so",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]);
    }

    public void onWindow(WindowListener listener) {
        addWindowListener(listener);
    }

    private JPanel formPanel(Object... pairs) {
        JPanel panel = new JPanel(new GridLayout(pairs.length / 2, 2, 7, 7));
        for (int i = 0; i < pairs.length; i += 2) {
            panel.add(new JLabel(String.valueOf(pairs[i]) + ':'));
            panel.add((Component) pairs[i + 1]);
        }
        return panel;
    }

    public static final class RegistrationInput implements AutoCloseable {
        private final String username;
        private final char[] password;
        private final char[] confirmation;
        private final String displayName;
        private final String email;
        private final String phone;

        private RegistrationInput(
                String username,
                char[] password,
                char[] confirmation,
                String displayName,
                String email,
                String phone) {
            this.username = username;
            this.password = password;
            this.confirmation = confirmation;
            this.displayName = displayName;
            this.email = email;
            this.phone = phone;
        }

        public String getUsername() { return username; }
        public char[] getPassword() { return password.clone(); }
        public char[] getConfirmation() { return confirmation.clone(); }
        public String getDisplayName() { return displayName; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }

        @Override
        public void close() {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
        }
    }

    public static final class ProfileInput {
        private final String displayName;
        private final String email;
        private final String phone;

        private ProfileInput(String displayName, String email, String phone) {
            this.displayName = displayName;
            this.email = email;
            this.phone = phone;
        }

        public String getDisplayName() { return displayName; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
    }

    public static final class PasswordChangeInput implements AutoCloseable {
        private final char[] current;
        private final char[] next;
        private final char[] confirmation;

        private PasswordChangeInput(char[] current, char[] next, char[] confirmation) {
            this.current = current;
            this.next = next;
            this.confirmation = confirmation;
        }

        public char[] getCurrent() { return current.clone(); }
        public char[] getNext() { return next.clone(); }
        public char[] getConfirmation() { return confirmation.clone(); }

        @Override
        public void close() {
            Arrays.fill(current, '\0');
            Arrays.fill(next, '\0');
            Arrays.fill(confirmation, '\0');
        }
    }

    public static final class ProductInput {
        private final long productId;
        private final String code;
        private final String name;
        private final String description;

        private ProductInput(long productId, String code, String name, String description) {
            this.productId = productId;
            this.code = code;
            this.name = name;
            this.description = description;
        }

        public long getProductId() { return productId; }
        public String getCode() { return code; }
        public String getName() { return name; }
        public String getDescription() { return description; }
    }

    public static final class AuctionInput {
        private final ClientProduct product;
        private final String startPrice;
        private final String minBidIncrement;
        private final String durationMinutes;

        private AuctionInput(
                ClientProduct product,
                String startPrice,
                String minBidIncrement,
                String durationMinutes) {
            this.product = product;
            this.startPrice = startPrice;
            this.minBidIncrement = minBidIncrement;
            this.durationMinutes = durationMinutes;
        }

        public ClientProduct getProduct() { return product; }
        public String getStartPrice() { return startPrice; }
        public String getMinBidIncrement() { return minBidIncrement; }
        public String getDurationMinutes() { return durationMinutes; }
    }
}
