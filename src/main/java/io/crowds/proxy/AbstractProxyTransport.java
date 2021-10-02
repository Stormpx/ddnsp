package io.crowds.proxy;

import io.netty.channel.*;

public abstract class AbstractProxyTransport implements ProxyTransport {

    protected ProxyOption proxyOption;
    protected EventLoopGroup eventLoopGroup;
    protected ChannelCreator channelCreator;

    public AbstractProxyTransport(ProxyOption proxyOption, EventLoopGroup eventLoopGroup, ChannelCreator channelCreator) {
        this.proxyOption = proxyOption;
        this.eventLoopGroup = eventLoopGroup;
        this.channelCreator = channelCreator;
    }
}
