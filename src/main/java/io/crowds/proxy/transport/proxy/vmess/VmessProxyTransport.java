package io.crowds.proxy.transport.proxy.vmess;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.proxy.AbstractProxyTransport;
import io.crowds.proxy.transport.proxy.xudp.MuxCodec;
import io.crowds.proxy.transport.proxy.xudp.XUDPHandler;
import io.crowds.proxy.transport.proxy.xudp.XUDPOption;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;

import java.util.Objects;

public class VmessProxyTransport extends AbstractProxyTransport<VmessOption> {

    private final XUDPOption xudp;

    public VmessProxyTransport(ChannelCreator channelCreator,VmessOption vmessOption) {
        Objects.requireNonNull(vmessOption);
        super(channelCreator,vmessOption);
        this.xudp = vmessOption.getXudp() == null? new XUDPOption() : vmessOption.getXudp();
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
        return new Destination(NetAddr.of(getProtocolOption().getAddress()),TP.TCP);
    }

    @Override
    protected Future<Channel> proxy(Channel channel, NetLocation netLocation) {
        boolean xudpFlag = xudpEnabled(netLocation);

        VmessOption vmessOption = getProtocolOption();
        VmessHandler vmessHandler = new VmessHandler(channel, vmessOption.getUser(), vmessOption.getSecurity(), xudpFlag, netLocation);
        channel.pipeline()
               .addLast(handlerName().with("codec"), new VmessMessageCodec())
               .addLast(handlerName().with("handler"), vmessHandler);

        if (xudpFlag){
            byte[] globalId = XUDPHandler.getGlobalId(netLocation.getSrc());
            channel.pipeline().addLast(new MuxCodec(),new XUDPHandler(globalId));
        }

        return channel.eventLoop().newSucceededFuture(channel);
    }

    //only XUDP can carry several UDP targets over one channel, so full-cone applies to XUDP sessions only
    @Override
    protected boolean fullConeSupport(ProxyContext proxyContext) {
        return xudpEnabled(proxyContext.getNetLocation());
    }
}
