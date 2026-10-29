package vn.ptit.btl16.client.view;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;

public final class LoginPanel extends JPanel {
    private final JTextField usernameField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JButton loginButton = new JButton("Đăng nhập");
    private final JButton registerButton = new JButton("Đăng ký");
    private final JButton reconnectButton = new JButton("Kết nối lại");
    private final JLabel hintLabel = new JLabel("Chưa có tài khoản? Bấm Đăng ký để bắt đầu.");
    private boolean busy;
    private boolean connected;

    public LoginPanel() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(7, 7, 7, 7);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        add(new JLabel("HỆ THỐNG ĐẤU GIÁ TRỰC TUYẾN THỜI GIAN THỰC", JLabel.CENTER), c);

        c.gridy++;
        add(new JLabel("Java TCP - Nhóm 16", JLabel.CENTER), c);

        c.gridwidth = 1;
        c.gridy++;
        c.gridx = 0;
        add(new JLabel("Username:"), c);
        c.gridx = 1;
        add(usernameField, c);

        c.gridy++;
        c.gridx = 0;
        add(new JLabel("Mật khẩu:"), c);
        c.gridx = 1;
        add(passwordField, c);

        c.gridy++;
        c.gridx = 0;
        add(loginButton, c);
        c.gridx = 1;
        add(registerButton, c);

        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 2;
        add(reconnectButton, c);

        c.gridy++;
        add(hintLabel, c);

        passwordField.addActionListener(event -> loginButton.doClick());
        refreshEnabledState();
    }

    public String getUsername() { return usernameField.getText(); }
    public char[] getPassword() { return passwordField.getPassword(); }
    public void clearPassword() { passwordField.setText(""); }

    public void setBusy(boolean busy) {
        this.busy = busy;
        refreshEnabledState();
    }

    public void setConnectionAvailable(boolean connected) {
        this.connected = connected;
        refreshEnabledState();
    }

    private void refreshEnabledState() {
        usernameField.setEnabled(!busy);
        passwordField.setEnabled(!busy);
        loginButton.setEnabled(!busy && connected);
        registerButton.setEnabled(!busy && connected);
        reconnectButton.setEnabled(!busy && !connected);
    }

    public void onLogin(ActionListener listener) { loginButton.addActionListener(listener); }
    public void onRegister(ActionListener listener) { registerButton.addActionListener(listener); }
    public void onReconnect(ActionListener listener) { reconnectButton.addActionListener(listener); }
}
