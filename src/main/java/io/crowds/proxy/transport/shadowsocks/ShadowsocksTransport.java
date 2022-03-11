package io.crowds.proxy.transport.shadowsocks;

import io.crowds.proxy.*;
import io.crowds.proxy.common.BaseChannelInitializer;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.UdpChannel;
import io.crowds.proxy.transport.common.DirectTransport;
import io.crowds.proxy.transport.common.Transport;
import io.crowds.proxy.transport.direct.DirectProxyTransport;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.logging.LogLevel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.ExecutionException;

public class ShadowsocksTransport extends DirectProxyTransport {

    private ShadowsocksOption shadowsocksOption;

    public ShadowsocksTransport(ChannelCreator channelCreator, ShadowsocksOption shadowsocksOption) {
        super(channelCreator);
        this.shadowsocksOption = shadowsocksOption;

    }

    @Override
    public String getTag() {
        return shadowsocksOption.getName();
    }

    @Override
    public Future<EndPoint> createEndPoint(ProxyContext proxyContext) {
        EventLoop eventLoop = proxyContext.getEventLoop();
        NetLocation netLocation = proxyContext.getNetLocation();
        Promise<EndPoint> promise = eventLoop.newPromise();
        BaseChannelInitializer initializer = new BaseChannelInitializer()
                .connIdle(shadowsocksOption.getConnIdle())
                .initializer(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addFirst("AEADCodec", netLocation.getTp() == TP.TCP ? AEADCodec.tcp(shadowsocksOption) : AEADCodec.udp(shadowsocksOption));
                    }
                });

        NetLocation serverLocation = new NetLocation(netLocation.getSrc(), new NetAddr(shadowsocksOption.getAddress()), netLocation.getTp());
        if (netLocation.getTp()==TP.TCP){
            createTcp(eventLoop,serverLocation,initializer)
                    .addListener((FutureListener<Channel>) future -> {
                        if (!future.isSuccess()){
                            promise.tryFailure(future.cause());
                            return;
                        }
                        promise.trySuccess(new ShadowsocksEndpoint(future.get(), shadowsocksOption,netLocation));
                    });
        }else{
            createUdp(shadowsocksOption.getName(),eventLoop, serverLocation.getSrc(), initializer)
                    .addListener((FutureListener<UdpChannel>) future -> {
                        if (!future.isSuccess()){
                            promise.tryFailure(future.cause());
                            return;
                        }
                        UdpChannel udpChannel = future.get();
                        if (proxyContext.fallbackPacketHandler()!=null)
                            udpChannel.fallbackHandler(proxyContext.fallbackPacketHandler());

                        promise.trySuccess(new ShadowsocksEndpoint(udpChannel, shadowsocksOption,netLocation, serverLocation.getDest()));
                    });
        }

        return promise;
    }
}
