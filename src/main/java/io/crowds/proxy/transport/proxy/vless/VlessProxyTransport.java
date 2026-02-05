package io.crowds.proxy.transport.proxy.vless;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;
import io.crowds.proxy.common.HandlerName;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.TlsOption;
import io.crowds.proxy.transport.Transport;
import io.crowds.proxy.transport.proxy.AbstractProxyTransport;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;

public class VlessProxyTransport extends AbstractProxyTransport<VlessOption>{

    private final Vless.Flow flow;
    private final Destination destination;

    public VlessProxyTransport(ChannelCreator channelCreator, VlessOption vlessOption) {
        super(channelCreator, vlessOption);
        this.flow = Vless.Flow.of(vlessOption.getFlow());
        this.destination=new Destination(NetAddr.of(vlessOption.getAddress()),TP.TCP);
        TlsOption tls = vlessOption.getTls();
        if ((tls==null || !tls.isEnable()) && this.flow == Vless.Flow.XRV){
            throw new IllegalArgumentException("XTLS flow requires TLS to be enabled");
        }
    }


    @Override
    public Destination getRemote(TP tp) {
        return destination;
    }

    @Override
    protected Future<Channel> proxy(Channel channel, NetLocation netLocation) {
        VlessOption vlessOption = getProtocolOption();
        HandlerName baseName = handlerName();
        Destination dest = new Destination(netLocation);
        channel.pipeline()
               .addLast(baseName.with("codec"), new VlessCodec(dest))
               .addLast(baseName.with("handler"), new VlessHandler(flow, vlessOption.getUUID(), dest));
        return channel.eventLoop().newSucceededFuture(channel);
    }
}
