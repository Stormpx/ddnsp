package io.crowds.proxy.transport.proxy.shadowsocks;

import io.crowds.Ddnsp;
import io.crowds.proxy.*;
import io.crowds.proxy.common.HandlerName;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.Transport;
import io.crowds.proxy.transport.proxy.FullConeProxyTransport;
import io.crowds.util.AddrType;
import io.crowds.util.Async;
import io.crowds.util.Inet;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
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
    protected Destination getRemote(TP tp) {
        return new Destination(NetAddr.of(shadowsocksOption.getAddress()),tp);
    }



    @Override
    protected Future<Channel> proxy(Channel channel, NetLocation netLocation) {
        HandlerName baseName = handlerName();
        var codecName = baseName.with("codec");
        var handlerName = baseName.with("handler");
        if (netLocation.getTp()==TP.TCP){
            channel.pipeline()
                   .addLast(codecName,AEAD.tcp(shadowsocksOption,saltPool))
                   .addLast(handlerName,new ShadowsocksHandler(shadowsocksOption,netLocation));
            return channel.eventLoop().newSucceededFuture(channel);
        }else{
            NetAddr server = NetAddr.of(shadowsocksOption.getAddress());
            channel.pipeline()
                   .addLast(codecName,AEAD.udp(shadowsocksOption))
                   .addLast(handlerName,new ShadowsocksHandler(shadowsocksOption,server));
            return channel.eventLoop().newSucceededFuture(channel);
        }
    }

    @Override
    public Future<Channel> createChannel(EventLoop eventLoop, NetLocation netLocation, Transport delegate) throws Exception {
        Promise<Channel> promise = eventLoop.newPromise();

        NetAddr src = netLocation.getSrc();
        TP tp = netLocation.getTp();
        Destination destination = new Destination(NetAddr.of(shadowsocksOption.getAddress()), tp);

        Async.toFuture(this.transport.createChannel(eventLoop,destination, src.isIpv4()? AddrType.IPV4:AddrType.IPV6,delegate))
             .compose(it->Async.toFuture(proxy(it,netLocation)))
             .onComplete(Async.futureCascadeCallback(promise));

        return promise;
    }
}
