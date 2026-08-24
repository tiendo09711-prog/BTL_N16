package vn.ptit.btl16.server.account.controller;

import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.util.Times;
import vn.ptit.btl16.server.account.model.UserAccount;
import vn.ptit.btl16.server.account.service.AccountService;
import vn.ptit.btl16.server.account.service.LoginResult;
import vn.ptit.btl16.server.auction.service.RoomManager;
import vn.ptit.btl16.server.routing.RequestContext;
import vn.ptit.btl16.server.session.UserSession;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Maps account protocol messages to AccountService calls. */
public final class AccountController {
    private final AccountService accounts;
    private final RoomManager rooms;

    public AccountController(AccountService accounts, RoomManager rooms) {
        this.accounts = accounts;
        this.rooms = rooms;
    }

    public void handleRegister(RequestContext context) throws Exception {
        char[] password = context.value("password").toCharArray();
        try {
            UserAccount user = accounts.register(
                    context.value("username"),
                    password,
                    context.value("displayName"),
                    context.value("email"),
                    context.value("phone"));
            Map<String, String> data = profileData(user);
            data.put("message", "Dang ky thanh cong");
            context.replySuccess(MessageType.REGISTER_RESULT, data);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public void handleLogin(RequestContext context) throws Exception {
        char[] password = context.value("password").toCharArray();
        try {
            LoginResult result = accounts.login(
                    context.value("username"),
                    password,
                    context.getConnection().getConnectionId(),
                    context.getConnection().getRemoteAddress());
            Map<String, String> data = loginData(result);
            data.put("message", "Dang nhap thanh cong");
            context.replySuccess(MessageType.LOGIN_RESULT, data);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public void handleResume(RequestContext context) throws Exception {
        LoginResult result = accounts.resume(
                context.value("sessionToken"),
                context.getConnection().getConnectionId(),
                context.getConnection().getRemoteAddress());
        Map<String, String> data = loginData(result);
        data.put("message", "Phuc hoi session thanh cong");
        context.replySuccess(MessageType.RESUME_SESSION_RESULT, data);
    }

    public void handleLogout(RequestContext context) throws Exception {
        String connectionId = context.getConnection().getConnectionId();
        rooms.removeConnection(connectionId);
        accounts.logout(connectionId);
        context.replySuccess(
                MessageType.LOGOUT_RESULT,
                Map.of("message", "Da dang xuat"));
    }

    public void handleGetProfile(RequestContext context) throws Exception {
        UserAccount user = accounts.getProfile(context.requireSession().getUserId());
        context.replySuccess(MessageType.PROFILE_RESULT, profileData(user));
    }

    public void handleUpdateProfile(RequestContext context) throws Exception {
        UserAccount user = accounts.updateProfile(
                context.requireSession().getUserId(),
                context.value("displayName"),
                context.value("email"),
                context.value("phone"));
        Map<String, String> data = profileData(user);
        data.put("message", "Cap nhat ho so thanh cong");
        context.replySuccess(MessageType.UPDATE_PROFILE_RESULT, data);
    }

    public void handleChangePassword(RequestContext context) throws Exception {
        char[] currentPassword = context.value("currentPassword").toCharArray();
        char[] newPassword = context.value("newPassword").toCharArray();
        try {
            accounts.changePassword(
                    context.requireSession().getUserId(),
                    currentPassword,
                    newPassword);
            context.replySuccess(
                    MessageType.CHANGE_PASSWORD_RESULT,
                    Map.of("message", "Doi mat khau thanh cong"));
        } finally {
            Arrays.fill(currentPassword, '\0');
            Arrays.fill(newPassword, '\0');
        }
    }

    private Map<String, String> loginData(LoginResult result) {
        Map<String, String> data = profileData(result.getUser());
        UserSession session = result.getSession();
        data.put("sessionToken", session.getSessionToken());
        data.put("sessionStatus", session.getStatus().name());
        data.put("sessionCreatedAt", Times.wire(session.getCreatedAt()));
        data.put("resumeDeadline", Times.wire(session.getResumeDeadline()));
        return data;
    }

    private Map<String, String> profileData(UserAccount user) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("userId", Long.toString(user.getUserId()));
        data.put("username", user.getUsername());
        data.put("displayName", user.getDisplayName());
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());
        data.put("active", Boolean.toString(user.isActive()));
        data.put("createdAt", Times.wire(user.getCreatedAt()));
        data.put("updatedAt", Times.wire(user.getUpdatedAt()));
        data.put("lastLoginAt", Times.wire(user.getLastLoginAt()));
        return data;
    }
}
