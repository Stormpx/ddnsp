package io.crowds.proxy.transport.proxy.direct;

import io.crowds.Ddnsp;
import io.crowds.proxy.*;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.proxy.FullConeProxyTransport;
import io.crowds.util.AddrType;
import io.crowds.util.Async;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.address.DynamicAddressConnectHandler;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.*;

import java.net.*;
import java.util.Optional;

public class DirectProxyTransport extends FullConeProxyTransport {
    public final static AttributeKey<Void> DIRECT_FLAG =AttributeKey.valueOf("direct_flag");
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

        if (netLocation.getTp()==TP.TCP){
            channel.attr(DIRECT_FLAG);
        }
        return channel.eventLoop().newSucceededFuture(channel);
    }


}
