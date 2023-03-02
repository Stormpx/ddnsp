package io.crowds.proxy.transport.proxy.vmess;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.proxy.AbstractProxyTransport;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;

import java.util.Objects;

public class VmessProxyTransport extends AbstractProxyTransport {


    private VmessOption vmessOption;


    public VmessProxyTransport(ChannelCreator channelCreator,VmessOption vmessOption) {
        super( channelCreator,vmessOption);
        Objects.requireNonNull(vmessOption);
        this.vmessOption=vmessOption;
    }

    @Override
    public String getTag() {
        return vmessOption.getName();
    }

    @Override
    protected Destination getRemote(TP tp) {
        return new Destination(NetAddr.of(vmessOption.getAddress()),TP.TCP);
    }

    @Override
    protected Future<Channel> proxy(Channel channel, NetLocation netLocation) {
        new VmessHandler(channel,vmessOption,netLocation).handshake();
        return channel.eventLoop().newSucceededFuture(channel);
    }


    @Override
    public Future<EndPoint> createEndPoint(ProxyContext proxyContext) throws Exception {
        return super.createEndPoint(proxyContext);
    }
}
