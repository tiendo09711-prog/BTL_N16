package vn.ptit.btl16.server.module;

import vn.ptit.btl16.server.routing.MessageRouter;

public interface ServerModule {
    void register(MessageRouter router);
}
