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


    private ShadowsocksHandler newHandler(NetLocation netLocation){
        return netLocation.getTp()==TP.TCP?
                new ShadowsocksHandler(shadowsocksOption,netLocation):
                new ShadowsocksHandler(shadowsocksOption,new NetAddr(shadowsocksOption.getAddress()));
    }

    @Override
    protected Future<Channel> proxy(Channel channel, NetLocation netLocation) {
        channel.pipeline()
                .addLast(netLocation.getTp()==TP.TCP?AEADCodec.tcp(shadowsocksOption):AEADCodec.udp(shadowsocksOption))
                .addLast(newHandler(netLocation));

        return channel.eventLoop().newSucceededFuture(channel);
    }

}
