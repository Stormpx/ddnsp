package io.crowds.proxy.services;

import io.vertx.core.Future;

public interface ProtocolServer {

    Future<Void> start();

}
