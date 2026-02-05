package io.crowds.proxy.transport.proxy.direct;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.ProxyContext;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.Transport;
import io.crowds.proxy.transport.proxy.FullConeProxyTransport;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;

import java.util.Optional;

public class DirectProxyTransport extends FullConeProxyTransport<ProtocolOption> {

    public DirectProxyTransport(ChannelCreator channelCreator) {
        this(channelCreator,new ProtocolOption());
    }


    public DirectProxyTransport(ChannelCreator channelCreator, ProtocolOption protocolOption) {
        super(channelCreator,protocolOption);
    }

    @Override
    public String getTag() {
        return Optional.ofNullable(getProtocolOption())
                .map(ProtocolOption::getName)
                .orElse("direct");
    }

    @Override
    protected Future<Channel> proxy(Channel channel, NetLocation netLocation) {

        if (netLocation.getTp()==TP.TCP){
            channel.attr(ProxyContext.SEND_ZC_SUPPORTED);
        }
        return channel.eventLoop().newSucceededFuture(channel);
    }


}
