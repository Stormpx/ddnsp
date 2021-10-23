package io.crowds.proxy;

import io.netty.channel.*;

public abstract class AbstractProxyTransport implements ProxyTransport {

    protected EventLoopGroup eventLoopGroup;
    protected ChannelCreator channelCreator;

    public AbstractProxyTransport( EventLoopGroup eventLoopGroup, ChannelCreator channelCreator) {
        this.eventLoopGroup = eventLoopGroup;
        this.channelCreator = channelCreator;
    }
}
