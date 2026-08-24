package vn.ptit.btl16.server.account.service;

import vn.ptit.btl16.server.account.model.UserAccount;
import vn.ptit.btl16.server.session.UserSession;

public final class LoginResult {
    private final UserAccount user;
    private final UserSession session;

    public LoginResult(UserAccount user, UserSession session) {
        this.user = user;
        this.session = session;
    }

    public UserAccount getUser() { return user; }
    public UserSession getSession() { return session; }
}
