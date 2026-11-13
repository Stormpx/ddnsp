package io.crowds.proxy.transport.proxy.vless;

import io.crowds.proxy.*;
import io.crowds.proxy.common.HandlerName;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.TlsOption;
import io.crowds.proxy.transport.proxy.AbstractProxyTransport;
import io.crowds.proxy.transport.proxy.xudp.MuxCodec;
import io.crowds.proxy.transport.proxy.xudp.XUDPHandler;
import io.crowds.proxy.transport.proxy.xudp.XUDPOption;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;

public class VlessProxyTransport extends AbstractProxyTransport<VlessOption> {

    private final Vless.Flow flow;
    private final Destination destination;
    private final XUDPOption xudp;

    public VlessProxyTransport(ChannelCreator channelCreator, VlessOption vlessOption) {
        super(channelCreator, vlessOption);
        this.flow = Vless.Flow.of(vlessOption.getFlow());
        this.destination=new Destination(NetAddr.of(vlessOption.getAddress()),TP.TCP);
        TlsOption tls = vlessOption.getTls();
        if ((tls==null || !tls.isEnable()) && this.flow == Vless.Flow.XRV){
            throw new IllegalArgumentException("XTLS flow requires TLS to be enabled");
        }
        this.xudp = vlessOption.getXudp() == null? new XUDPOption() : vlessOption.getXudp();
        //only XUDP can carry several UDP targets over one channel, so full-cone applies to XUDP sessions only
        fullCone(ctx->xudpEnabled(ctx.getNetLocation()));
    }

    /**
     * Whether this UDP session uses XUDP (only XUDP can carry several targets over one channel).
     */
    private boolean xudpEnabled(NetLocation netLocation){
        if (netLocation.getTp()!=TP.UDP || !xudp.isEnable()){
            return false;
        }
        int port = netLocation.getDst().getPort();
        return port != 443 || xudp.isProxy443();
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
        boolean xudpSetup = xudpEnabled(netLocation);

        channel.pipeline()
               .addLast(baseName.with("codec"), new VlessCodec())
               .addLast(baseName.with("handler"), new VlessHandler(flow, vlessOption.getUUID(), dest,xudpSetup));

        if (xudpSetup){
            byte[] globalId = XUDPHandler.getGlobalId(netLocation.getSrc());
            channel.pipeline().addLast(new MuxCodec(),new XUDPHandler(globalId));
        }
        return channel.eventLoop().newSucceededFuture(channel);
    }
}
