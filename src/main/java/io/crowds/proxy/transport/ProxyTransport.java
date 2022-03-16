package io.crowds.proxy.transport;


import io.crowds.proxy.ProxyContext;
import io.crowds.proxy.transport.EndPoint;
import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.concurrent.Future;

public interface ProxyTransport {

    String getTag();

    Future<EndPoint> createEndPoint(ProxyContext proxyContext) throws Exception;


}
