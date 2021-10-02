package io.crowds.proxy.transport.vmess;

import io.crowds.proxy.*;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;

import java.net.SocketAddress;

public class VmessProxyTransport extends AbstractProxyTransport {


    public VmessProxyTransport(ProxyOption proxyOption, EventLoopGroup eventLoopGroup, ChannelCreator channelCreator) {
        super(proxyOption, eventLoopGroup, channelCreator);
    }

    @Override
    public Future<EndPoint> createEndPoint(NetLocation netLocation) {
        return null;
    }
}
