package io.crowds.proxy;


import io.crowds.proxy.transport.EndPoint;
import io.netty.util.concurrent.Future;

public interface ProxyTransport {

    String getTag();

    Future<EndPoint> createEndPoint(ProxyContext proxyContext) throws Exception;


}
