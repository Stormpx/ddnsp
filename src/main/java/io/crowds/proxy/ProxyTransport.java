package io.crowds.proxy;


import io.netty.util.concurrent.Future;

import java.net.SocketAddress;

public interface ProxyTransport {

    Future<EndPoint> createEndPoint(NetLocation netLocation);


}
