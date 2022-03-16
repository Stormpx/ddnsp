package io.crowds.proxy.transport.proxy.direct;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.proxy.FullConeProxyTransport;
import io.netty.channel.*;
import io.netty.util.concurrent.*;

import java.util.Optional;

public class DirectProxyTransport extends FullConeProxyTransport {

    private ProtocolOption protocolOption;

    public DirectProxyTransport(ChannelCreator channelCreator) {
        this(channelCreator,new ProtocolOption());
    }


    public DirectProxyTransport(ChannelCreator channelCreator, ProtocolOption protocolOption) {
        super(channelCreator,protocolOption);
        this.protocolOption = protocolOption;
    }

    @Override
    public String getTag() {
        return Optional.ofNullable(protocolOption)
                .map(ProtocolOption::getName)
                .orElse("direct");
    }

    @Override
    protected Future<Channel> proxy(Channel channel, NetLocation netLocation) {
        return channel.eventLoop().newSucceededFuture(channel);
    }

}
