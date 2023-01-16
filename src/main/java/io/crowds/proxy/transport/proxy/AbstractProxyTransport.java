package io.crowds.proxy.transport.proxy;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.*;
import io.netty.channel.*;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

public abstract class AbstractProxyTransport implements ProxyTransport {

    protected ChannelCreator channelCreator;
    protected Transport transport;

    public AbstractProxyTransport(ChannelCreator channelCreator,ProtocolOption protocolOption) {
        this.channelCreator = channelCreator;
        this.transport= TransportFactory.newTransport(protocolOption,channelCreator);
    }

    protected Destination getDest(TP tp){return null;}

    protected abstract Future<Channel> proxy(Channel channel, NetLocation netLocation);

    private Destination resolve(Destination dst){
        if (dst.addr() instanceof DomainNetAddr domain){
            return new Destination(domain.resolve(),dst.tp());
        }
        return dst;
    }

    protected Future<Channel> createChannel(ProxyContext proxyContext) throws Exception {

        NetLocation netLocation = proxyContext.getNetLocation();
        Destination destination = getDest(netLocation.getTp());
        if (destination==null) {
            destination = new Destination(netLocation.getDest(),netLocation.getTp());
        }
        destination = resolve(destination);
        Promise<Channel> promise = proxyContext.getEventLoop().newPromise();
        transport.createChannel(proxyContext.getEventLoop(),destination)
                .addListener((FutureListener<Channel>) future -> {
                    if (!future.isSuccess()){
                        promise.tryFailure(future.cause());
                        return;
                    }
                    proxy(future.get(),netLocation)
                            .addListener(f->{
                                if (f.isSuccess()){
                                    promise.trySuccess((Channel) f.get());
                                }else {
                                    promise.tryFailure(f.cause());
                                }
                            });
                });
        return promise;
    }

    @Override
    public Future<EndPoint> createEndPoint(ProxyContext proxyContext) throws Exception {
        NetLocation netLocation = proxyContext.getNetLocation();

        Promise<EndPoint> promise = proxyContext.getEventLoop().newPromise();
        createChannel(proxyContext)
                .addListener((FutureListener<Channel>)f->{
                    if (!f.isSuccess()){
                        promise.tryFailure(f.cause());
                        return;
                    }
                    Channel ch = f.get();
                    if (netLocation.getTp()==TP.TCP){
                        promise.trySuccess(new TcpEndPoint(ch));
                    }else{
                        UdpChannel udpChannel = new UdpChannel(ch,netLocation.getSrc().getAsInetAddr());
                        if (proxyContext.fallbackPacketHandler()!=null)
                            udpChannel.fallbackHandler(proxyContext.fallbackPacketHandler());
                        promise.trySuccess(new UdpEndPoint(udpChannel,netLocation.getDest()));
                    }

                });

        return promise;
    }
}
