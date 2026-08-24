package vn.ptit.btl16.server.routing;

@FunctionalInterface
public interface MessageHandler {
    void handle(RequestContext context) throws Exception;
}
