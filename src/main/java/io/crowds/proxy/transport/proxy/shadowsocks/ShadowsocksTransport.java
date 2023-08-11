package io.crowds.proxy.transport.proxy.shadowsocks;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.crowds.Ddnsp;
import io.crowds.proxy.*;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.proxy.FullConeProxyTransport;
import io.crowds.util.AddrType;
import io.crowds.util.Async;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
        if (netLocation.getTp()==TP.TCP){
            channel.pipeline()
                   .addLast(AEAD.tcp(shadowsocksOption,saltPool))
                   .addLast(new ShadowsocksHandler(shadowsocksOption,netLocation));
            return channel.eventLoop().newSucceededFuture(channel);
        }else{
            InetSocketAddress serverAddr = shadowsocksOption.getAddress();
            if (serverAddr.isUnresolved()){
                Promise<Channel> promise=channel.eventLoop().newPromise();
                boolean ipv4 = netLocation.getSrc().getAsInetAddr().getAddress() instanceof Inet4Address;
                Async.toCallback(channel.eventLoop(), Ddnsp.dnsResolver().resolve(serverAddr.getHostString(),ipv4? AddrType.IPV4:AddrType.IPV6))
                        .addListener(rf->{
                            if (!rf.isSuccess()){
                                promise.tryFailure(rf.cause());
                                return;
                            }
                            channel.pipeline()
                                   .addLast(AEAD.udp(shadowsocksOption))
                                   .addLast(new ShadowsocksHandler(shadowsocksOption,NetAddr.of(new InetSocketAddress((InetAddress) rf.get(),serverAddr.getPort()))));
                            promise.trySuccess(channel);
                        });
                return promise;
            }else{
                channel.pipeline()
                       .addLast(AEAD.udp(shadowsocksOption))
                       .addLast(new ShadowsocksHandler(shadowsocksOption,new NetAddr(shadowsocksOption.getAddress())));
                return channel.eventLoop().newSucceededFuture(channel);
            }

        }

    }

}
