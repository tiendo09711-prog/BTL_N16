package vn.ptit.btl16.server.routing;

import vn.ptit.btl16.common.protocol.ErrorCode;
import vn.ptit.btl16.common.protocol.MessageKind;
import vn.ptit.btl16.common.protocol.MessageType;
import vn.ptit.btl16.common.protocol.ProtocolLimits;
import vn.ptit.btl16.common.protocol.WireMessage;
import vn.ptit.btl16.common.validation.ValidationException;
import vn.ptit.btl16.server.ServerSequence;
import vn.ptit.btl16.server.account.repository.RepositoryException;
import vn.ptit.btl16.server.account.service.AccountException;
import vn.ptit.btl16.server.auction.repository.AuctionRepositoryException;
import vn.ptit.btl16.server.auction.service.AuctionException;
import vn.ptit.btl16.server.network.ServerConnection;
import vn.ptit.btl16.server.session.SessionException;
import vn.ptit.btl16.server.session.SessionManager;
import vn.ptit.btl16.server.session.UserSession;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MessageRouter {
    private final Map<MessageType, Route> routes = new ConcurrentHashMap<>();
    private final SessionManager sessions;
    private final ServerSequence sequence;

    public MessageRouter(SessionManager sessions, ServerSequence sequence) {
        this.sessions = sessions;
        this.sequence = sequence;
    }

    public void register(MessageType type, boolean authenticationRequired, MessageHandler handler) {
        Route previous = routes.putIfAbsent(type, new Route(authenticationRequired, handler));
        if (previous != null) {
            throw new IllegalStateException("MessageType already has a handler: " + type);
        }
    }

    public void route(ServerConnection connection, WireMessage request) {
        RequestContext context = new RequestContext(connection, request, null, sequence);
        try {
            if (request.getVersion() != ProtocolLimits.CURRENT_VERSION) {
                context.replyError(
                        ErrorCode.UNSUPPORTED_VERSION,
                        "Server supports protocol version " + ProtocolLimits.CURRENT_VERSION);
                return;
            }
            if (request.getKind() != MessageKind.REQUEST) {
                context.replyError(ErrorCode.INVALID_MESSAGE, "Client may only send REQUEST messages");
                return;
            }
            Route route = routes.get(request.getType());
            if (route == null) {
                context.replyError(
                        ErrorCode.HANDLER_NOT_FOUND,
                        "No handler for " + request.getType());
                return;
            }

            Optional<UserSession> session = sessions.findByConnection(connection.getConnectionId());
            if (route.authenticationRequired && session.isEmpty()) {
                context.replyError(ErrorCode.AUTH_REQUIRED, "Login is required");
                return;
            }
            if (session.isPresent()) {
                sessions.touchByConnection(connection.getConnectionId());
            }
            context = new RequestContext(connection, request, session.orElse(null), sequence);
            route.handler.handle(context);
        } catch (ValidationException | IllegalArgumentException exception) {
            safeError(context, ErrorCode.VALIDATION_ERROR, exception.getMessage());
        } catch (AccountException exception) {
            safeError(context, exception.getErrorCode(), exception.getMessage());
        } catch (SessionException exception) {
            safeError(context, exception.getErrorCode(), exception.getMessage());
        } catch (AuctionException exception) {
            safeError(context, exception.getErrorCode(), exception.getMessage());
        } catch (RepositoryException | AuctionRepositoryException exception) {
            System.err.println("[DB] " + exception.getMessage());
            safeError(context, ErrorCode.DATABASE_ERROR, "Database operation failed");
        } catch (Exception exception) {
            System.err.println("[ROUTER] " + request.getType() + " error: " + exception.getMessage());
            safeError(context, ErrorCode.INTERNAL_ERROR, "Internal server error");
        }
    }

    private void safeError(RequestContext context, ErrorCode code, String message) {
        try {
            context.replyError(code, message == null ? code.name() : message);
        } catch (IOException exception) {
            context.getConnection().close();
        }
    }

    private static final class Route {
        private final boolean authenticationRequired;
        private final MessageHandler handler;

        private Route(boolean authenticationRequired, MessageHandler handler) {
            this.authenticationRequired = authenticationRequired;
            this.handler = handler;
        }
    }
}
