package io.crowds.proxy.transport.proxy.vless;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;
import io.crowds.proxy.common.HandlerName;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.Transport;
import io.crowds.proxy.transport.proxy.AbstractProxyTransport;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;

public class VlessProxyTransport extends AbstractProxyTransport {
    private VlessOption vlessOption;
    private Destination destination;
    public VlessProxyTransport(ChannelCreator channelCreator, VlessOption vlessOption) {
        super(channelCreator, vlessOption);
        this.vlessOption=vlessOption;
        this.destination=new Destination(NetAddr.of(vlessOption.getAddress()),TP.TCP);
    }

    @Override
    public String getTag() {
        return vlessOption.getName();
    }

    @Override
    protected Destination getRemote(TP tp) {
        return destination;
    }

    @Override
    protected Future<Channel> proxy(Channel channel, NetLocation netLocation, Transport delegate) {
        HandlerName baseName = handlerName();
        Destination dest = new Destination(netLocation);
        channel.pipeline()
               .addLast(baseName.with("codec"), new VlessCodec(dest))
               .addLast(baseName.with("handler"), new VlessHandler(vlessOption.getId(), dest));
        return channel.eventLoop().newSucceededFuture(channel);
    }
}
