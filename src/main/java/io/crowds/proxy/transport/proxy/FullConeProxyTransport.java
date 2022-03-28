package io.crowds.proxy.transport.proxy;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.UdpChannel;
import io.crowds.proxy.transport.UdpEndPoint;
import io.crowds.util.Lambdas;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class FullConeProxyTransport extends AbstractProxyTransport {

    private Map<NetAddr,Future<UdpChannel>> udpChannelMap;

    public FullConeProxyTransport(ChannelCreator channelCreator, ProtocolOption protocolOption) {
        super(channelCreator,protocolOption);
        this.udpChannelMap=new ConcurrentHashMap<>();
    }



    private Future<UdpChannel> createUdpChannel(ProxyContext proxyContext) throws Exception {
        Promise<UdpChannel> promise = proxyContext.getEventLoop().newPromise();
        createChannel(proxyContext)
                .addListener((FutureListener<Channel>) f->{
                    if (!f.isSuccess()){
                        return;
                    }
                    Channel channel = f.get();
                    UdpChannel udpChannel = new UdpChannel(channel);

                    promise.trySuccess(udpChannel);

                });
        return promise;
    }


    @Override
    public Future<EndPoint> createEndPoint(ProxyContext proxyContext) throws Exception {
        NetLocation netLocation = proxyContext.getNetLocation();
        if (netLocation.getTp()== TP.TCP){
            return super.createEndPoint(proxyContext);
        }else {
            Future<UdpChannel> channelFuture = udpChannelMap.computeIfAbsent(netLocation.getSrc(),
                    Lambdas.rethrowFunction(k->
                            createUdpChannel(proxyContext)
                                .addListener((FutureListener<UdpChannel>)future -> {
                                    if (!future.isSuccess()){
                                        udpChannelMap.remove(netLocation.getSrc());
                                    }
                                    if (proxyContext.fallbackPacketHandler()!=null)
                                        future.get().fallbackHandler(proxyContext.fallbackPacketHandler());
                                }))
            );

            Promise<EndPoint> promise = proxyContext.getEventLoop().newPromise();

            channelFuture
                    .addListener((FutureListener<UdpChannel>) f->{
                        if (!f.isSuccess()){
                            promise.tryFailure(f.cause());
                            return;
                        }
                        UdpChannel udpChannel = f.get();
                        promise.trySuccess(new UdpEndPoint(udpChannel,netLocation.getDest()));
                    });

            return promise;
        }


    }

}
