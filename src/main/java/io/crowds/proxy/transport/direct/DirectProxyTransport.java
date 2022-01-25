package io.crowds.proxy.transport.direct;

import io.crowds.proxy.*;
import io.crowds.proxy.common.BaseChannelInitializer;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.UdpChannel;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

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

    protected Future<EndPoint> createEndPoint0(String namespace,EventLoop eventLoop,NetLocation netLocation, ChannelInitializer<Channel> initializer) {
        Promise<EndPoint> promise = eventLoop.newPromise();

        try {
            NetAddr dest = netLocation.getDest();
            SocketAddress target= dest.getAddress();
            if (dest instanceof DomainNetAddr){
                target=((DomainNetAddr) dest).getResolveAddress();
            }
            TP tp = netLocation.getTp();
            if (TP.TCP== tp){
                var cf= channelCreator.createTcpChannel(eventLoop,target, initializer);

                cf.addListener(future -> {
                    if (future.isSuccess()){
                        promise.trySuccess(new TcpEndPoint(cf.channel()));
                    }else{
                        promise.tryFailure(future.cause());
                    }
                });
            }else{
                SocketAddress finalTarget = target;
                channelCreator.createDatagramChannel(namespace, (InetSocketAddress) netLocation.getSrc().getAddress(),new DatagramOption(),initializer)
                    .addListener((FutureListener<UdpChannel>) future -> {
                        if (!future.isSuccess()){
                            promise.tryFailure(future.cause());
                            return;
                        }
                        UdpEndPoint udpEndPoint = new UdpEndPoint(future.get(), (InetSocketAddress) finalTarget);
                        promise.trySuccess(udpEndPoint);
                    });


            }
        } catch (Exception e) {
            promise.tryFailure(e);
        }
        return promise;
    }

    @Override
    public Future<EndPoint> createEndPoint(ProxyContext proxyContext) throws Exception {
        NetLocation netLocation = proxyContext.getNetLocation();
        BaseChannelInitializer initializer = new BaseChannelInitializer();
        if (netLocation.getTp()==TP.UDP)
            initializer.connIdle(120);
        return createEndPoint0("direct", proxyContext.getEventLoop(),netLocation, initializer);
    }


}
