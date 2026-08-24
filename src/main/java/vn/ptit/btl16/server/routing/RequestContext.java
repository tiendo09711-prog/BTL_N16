package vn.ptit.btl16.server.routing;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.WireMessage;
import vn.ptit.btl16.server.ServerSequence;
import vn.ptit.btl16.server.network.ServerConnection;
import vn.ptit.btl16.server.session.UserSession;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class RequestContext {
    private final ServerConnection connection;
    private final WireMessage request;
    private final UserSession session;
    private final ServerSequence sequence;

    public RequestContext(
            ServerConnection connection,
            WireMessage request,
            UserSession session,
            ServerSequence sequence) {
        this.connection = connection;
        this.request = request;
        this.session = session;
        this.sequence = sequence;
    }

    public ServerConnection getConnection() { return connection; }
    public WireMessage getRequest() { return request; }
    public Optional<UserSession> getSession() { return Optional.ofNullable(session); }

    public UserSession requireSession() {
        if (session == null) {
            throw new IllegalStateException("This route requires authentication");
        }
        return session;
    }

    public String value(String key) {
        return request.getOrDefault(key, "");
    }

    public void reply(MessageType type, Map<String, String> data) throws IOException {
        connection.send(WireMessage.response(type, request, sequence.next(), data));
    }

    public void replySuccess(MessageType type, Map<String, String> extra) throws IOException {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("success", "true");
        if (extra != null) {
            data.putAll(extra);
        }
        reply(type, data);
    }

    public void replyFailure(MessageType type, ErrorCode code, String message) throws IOException {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("success", "false");
        data.put("errorCode", code.name());
        data.put("message", message == null ? "" : message);
        reply(type, data);
    }

    public void replyError(ErrorCode code, String message) throws IOException {
        replyFailure(MessageType.ERROR, code, message);
    }
}
