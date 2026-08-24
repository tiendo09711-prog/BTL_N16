package vn.ptit.btl16.selftest;

import vn.ptit.btl16.server.session.SessionManager;
import vn.ptit.btl16.server.session.SessionStatus;
import vn.ptit.btl16.server.session.UserSession;

import java.time.Duration;

public final class SessionManagerSelfTest {
    private SessionManagerSelfTest() {
    }

    public static void run() throws Exception {
        SessionManager sessions = new SessionManager(Duration.ofMillis(120));
        UserSession created = sessions.createSession(1L, "demo", "conn-1", "local");
        TestSupport.equals(SessionStatus.ACTIVE, created.getStatus(), "created status");
        TestSupport.equals(1, sessions.activeCount(), "active count");

        sessions.detachByConnection("conn-1");
        TestSupport.equals(0, sessions.activeCount(), "detached is not online");
        UserSession resumed = sessions.resumeSession(created.getSessionToken(), "conn-2", "local2");
        TestSupport.equals("conn-2", resumed.getConnectionId(), "resumed connection");

        sessions.detachByConnection("conn-2");
        Thread.sleep(170L);
        TestSupport.equals(1, sessions.cleanupExpired(), "expired cleanup");
        TestSupport.check(sessions.findByToken(created.getSessionToken()).isEmpty(), "token removed");
        System.out.println("[PASS] SessionManagerSelfTest");
    }
}
