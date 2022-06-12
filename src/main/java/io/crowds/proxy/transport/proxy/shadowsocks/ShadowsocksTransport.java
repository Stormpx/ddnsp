package io.crowds.proxy.transport.proxy.shadowsocks;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.proxy.FullConeProxyTransport;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;

import java.util.Objects;


public class ShadowsocksTransport extends FullConeProxyTransport {

    private ShadowsocksOption shadowsocksOption;
    private SaltPool saltPool;

    public ShadowsocksTransport(ChannelCreator channelCreator, ShadowsocksOption shadowsocksOption) {
        super(channelCreator,shadowsocksOption);
        this.shadowsocksOption = shadowsocksOption;
        Objects.requireNonNull(shadowsocksOption.getCipher());
        this.saltPool=switch (shadowsocksOption.getCipher()){
            case AES_128_GCM_2022,AES_256_GCM_2022 ->new SaltPool(channelCreator.getEventLoopGroup().next());
            default -> null;
        };
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
                .addLast(netLocation.getTp()==TP.TCP?AEAD.tcp(shadowsocksOption,saltPool):AEAD.udp(shadowsocksOption))
                .addLast(newHandler(netLocation));

        return channel.eventLoop().newSucceededFuture(channel);
    }

}
