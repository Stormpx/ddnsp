package io.crowds.proxy.transport.proxy;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.UdpChannel;
import io.crowds.proxy.transport.UdpEndPoint;
import io.crowds.util.Lambdas;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
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
        createChannel(proxyContext.getEventLoop(),proxyContext.getNetLocation())
                .addListener((FutureListener<Channel>) f->{
                    if (!f.isSuccess()){
                        return;
                    }
                    Channel channel = f.get();
                    UdpChannel udpChannel = new UdpChannel(channel,
                            proxyContext.getNetLocation().getSrc().getAsInetAddr(),false);

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
            EventLoop eventLoop = proxyContext.getEventLoop();
            NetAddr src = netLocation.getSrc();
            Future<UdpChannel> channelFuture = udpChannelMap.computeIfAbsent(src,
                    Lambdas.rethrowFunction(_->{
                            var udpFuture = createUdpChannel(proxyContext);
                            if (!udpFuture.isDone()||udpFuture.isSuccess()){
                                udpFuture.addListener((FutureListener<UdpChannel>)future -> {
                                    if (!future.isSuccess()){
                                        udpChannelMap.remove(src);
                                    }
                                    UdpChannel udpChannel = future.get();

                                    if (proxyContext.fallbackPacketHandler()!=null)
                                        udpChannel.fallbackHandler(proxyContext.fallbackPacketHandler());

                                    udpChannel.getChannel().closeFuture()
                                              .addListener(_->udpChannelMap.remove(src));
                                });
                            }
                            return udpFuture;
                    })
            );

            Promise<EndPoint> promise = eventLoop.newPromise();

            channelFuture
                    .addListener((FutureListener<UdpChannel>) f->{
                        if (!f.isSuccess()){
                            promise.tryFailure(f.cause());
                            return;
                        }
                        UdpChannel udpChannel = f.get();
                        promise.trySuccess(new UdpEndPoint(udpChannel,netLocation.getDst()));
                    });

            return promise;
        }


    }

}
