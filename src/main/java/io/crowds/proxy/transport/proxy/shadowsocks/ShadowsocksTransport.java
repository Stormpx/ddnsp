package io.crowds.proxy.transport.proxy.shadowsocks;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.proxy.FullConeProxyTransport;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;


public class ShadowsocksTransport extends FullConeProxyTransport {

    private ShadowsocksOption shadowsocksOption;

    public ShadowsocksTransport(ChannelCreator channelCreator, ShadowsocksOption shadowsocksOption) {
        super(channelCreator,shadowsocksOption);
        this.shadowsocksOption = shadowsocksOption;

    }

    @Override
    public String getTag() {
        return shadowsocksOption.getName();
    }

    @Override
    protected Destination getDest(TP tp) {
        return new Destination(NetAddr.of(shadowsocksOption.getAddress()),tp);
    }

    @Override
    protected Future<Channel> proxy(Channel channel, NetLocation netLocation) {
        if (netLocation.getTp()==TP.TCP){
            new ShadowsocksHandler(channel,shadowsocksOption,netLocation);
        }else {
            NetAddr serverAddr = new NetAddr(shadowsocksOption.getAddress());
            new ShadowsocksHandler(channel,shadowsocksOption,serverAddr);
        }

        return channel.eventLoop().newSucceededFuture(channel);
    }

}
