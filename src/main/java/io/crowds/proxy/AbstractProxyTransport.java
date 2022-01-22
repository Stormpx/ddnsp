package io.crowds.proxy;

import io.netty.channel.*;

public abstract class AbstractProxyTransport implements ProxyTransport {

    protected ChannelCreator channelCreator;

    public AbstractProxyTransport( ChannelCreator channelCreator) {
        this.channelCreator = channelCreator;
    }


}
