package io.crowds.proxy.transport.shadowsocks;

import io.crowds.proxy.*;
import io.crowds.proxy.common.BaseChannelInitializer;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.direct.DirectProxyTransport;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.ExecutionException;

public class ShadowsocksTransport extends DirectProxyTransport {

    private ShadowsocksOption shadowsocksOption;

    public ShadowsocksTransport(  ChannelCreator channelCreator, ShadowsocksOption shadowsocksOption) {
        super( channelCreator);
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

        var f=super.createEndPoint0(
                shadowsocksOption.getName(),
                eventLoop,
                new NetLocation(netLocation.getSrc(), new NetAddr(shadowsocksOption.getAddress()), netLocation.getTp()),
                initializer
        );
            f.addListener(future -> {
                if (!future.isSuccess()){
                    promise.tryFailure(future.cause());
                    return;
                }
                try {
                    EndPoint endPoint = f.get();
                    promise.trySuccess(new ShadowsocksEndpoint(endPoint,shadowsocksOption,netLocation));
                } catch (Exception e) {
                    promise.tryFailure(e);
                }
            });

        return promise;
    }
}
