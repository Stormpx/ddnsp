package io.crowds.proxy.transport.proxy.vmess;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.Transport;
import io.crowds.proxy.transport.proxy.AbstractProxyTransport;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;

import java.util.Objects;

public class VmessProxyTransport extends AbstractProxyTransport<VmessOption> {


    public VmessProxyTransport(ChannelCreator channelCreator,VmessOption vmessOption) {
        Objects.requireNonNull(vmessOption);
        super(channelCreator,vmessOption);
    }


    @Override
    public Destination getRemote(TP tp) {
        return new Destination(NetAddr.of(getProtocolOption().getAddress()),TP.TCP);
    }

    @Override
    protected Future<Channel> proxy(Channel channel, NetLocation netLocation) {
        new VmessHandler(handlerName(),channel,getProtocolOption(),netLocation).handshake();
        return channel.eventLoop().newSucceededFuture(channel);
    }


}
