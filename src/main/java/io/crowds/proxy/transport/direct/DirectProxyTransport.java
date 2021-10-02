package io.crowds.proxy.transport.direct;

import io.crowds.proxy.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;

import java.net.InetSocketAddress;

public class DirectProxyTransport extends AbstractProxyTransport {


    public DirectProxyTransport(ProxyOption proxyOption, EventLoopGroup eventLoopGroup, ChannelCreator channelCreator) {
        super(proxyOption, eventLoopGroup, channelCreator);
    }


    @Override
    public Future<EndPoint> createEndPoint(NetLocation netLocation) {
        DefaultPromise<EndPoint> promise = new DefaultPromise<>(eventLoopGroup.next());

        try {
            TP tp = netLocation.getTp();
            if (TP.TCP== tp){
                var cf= channelCreator.createTcpChannel(netLocation.getDest(), new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {

                    }
                });
                cf.addListener(future -> {
                    if (future.isSuccess()){
                        promise.trySuccess(new TcpEndPoint(cf.channel()));
                    }else{
                        promise.tryFailure(future.cause());
                    }
                });
            }else{
                promise.trySuccess(new UdpEndPoint(
                        channelCreator.createDatagramChannel((InetSocketAddress) netLocation.getSrc(),new DataGramChOption()),
                        (InetSocketAddress) netLocation.getDest())
                );
            }
        } catch (Exception e) {
            promise.tryFailure(e);
        }
        return promise;
    }
}
