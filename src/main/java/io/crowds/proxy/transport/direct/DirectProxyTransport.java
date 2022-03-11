package io.crowds.proxy.transport.direct;

import io.crowds.proxy.*;
import io.crowds.proxy.common.BaseChannelInitializer;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.UdpChannel;
import io.crowds.proxy.transport.common.DirectTransport;
import io.crowds.proxy.transport.common.TlsOption;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ExecutionException;

public class DirectProxyTransport extends AbstractProxyTransport {

    private ProtocolOption protocolOption;

    public DirectProxyTransport(ChannelCreator channelCreator) {
        super(channelCreator);
    }

    public DirectProxyTransport(ChannelCreator channelCreator, ProtocolOption protocolOption) {
        super(channelCreator);
        this.protocolOption = protocolOption;
    }

    @Override
    public String getTag() {
        return protocolOption==null?"direct":protocolOption.getName();
    }


    private NetAddr getDest(NetLocation netLocation){
        NetAddr dest = netLocation.getDest();
        if (dest instanceof DomainNetAddr dna){
            dest=dna.resolve();
        }
        return dest;
    }

    protected Future<Channel> createTcp(EventLoop eventLoop,NetLocation netLocation, ChannelInitializer<Channel> initializer){
        Promise<Channel> promise=eventLoop.newPromise();
        var cf= channelCreator.createTcpChannel(eventLoop,netLocation.getDest().getAddress(), initializer);
        cf.addListener(f->{
            if (!f.isSuccess()){
                promise.tryFailure(f.cause());
                return;
            }
            promise.trySuccess(cf.channel());
        });
        return promise;
    }

    protected Future<UdpChannel> createUdp(String namespace,EventLoop eventLoop,NetAddr tuple2, ChannelInitializer<Channel> initializer) {
        Promise<UdpChannel> promise=eventLoop.newPromise();

        channelCreator.createDatagramChannel(namespace, (InetSocketAddress) tuple2.getAddress(),new DatagramOption(),initializer)
                .addListener((FutureListener<UdpChannel>) future -> {
                    if (!future.isSuccess()){
                        promise.tryFailure(future.cause());
                        return;
                    }
                    promise.trySuccess(future.get());
                });
        return promise;
    }

    @Override
    public Future<EndPoint> createEndPoint(ProxyContext proxyContext) throws Exception {
        NetLocation netLocation = proxyContext.getNetLocation();
        if (netLocation.getDest() instanceof DomainNetAddr it){
            netLocation=new NetLocation(netLocation.getSrc(),it.resolve(),netLocation.getTp());
        }
        BaseChannelInitializer initializer = new BaseChannelInitializer();
        Promise<EndPoint> promise=proxyContext.getEventLoop().newPromise();
        if (netLocation.getTp()==TP.TCP){
            createTcp(proxyContext.getEventLoop(),netLocation, initializer)
                    .addListener((FutureListener<Channel>) future -> {
                        if (!future.isSuccess()){
                            promise.tryFailure(future.cause());
                            return;
                        }
                        promise.trySuccess(new TcpEndPoint(future.get()));
                    });
        } else {
            initializer.connIdle(120);
            NetLocation finalNetLocation = netLocation;
            createUdp("direct", proxyContext.getEventLoop(),netLocation.getSrc(), initializer)
                    .addListener((FutureListener<UdpChannel>) future -> {
                        if (!future.isSuccess()){
                            promise.tryFailure(future.cause());
                            return;
                        }

                        promise.trySuccess(new UdpEndPoint(future.get(), finalNetLocation.getDest()));
                    });
        }



        return promise;
    }


}
