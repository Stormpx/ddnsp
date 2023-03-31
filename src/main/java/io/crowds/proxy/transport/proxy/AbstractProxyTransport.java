package io.crowds.proxy.transport.proxy;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.*;
import io.crowds.util.AddrType;
import io.crowds.util.Async;
import io.crowds.util.Lambdas;
import io.netty.channel.*;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractProxyTransport implements ProxyTransport {
    private final static Logger logger= LoggerFactory.getLogger(AbstractProxyTransport.class);
    protected ChannelCreator channelCreator;
    protected Transport transport;

    public AbstractProxyTransport(ChannelCreator channelCreator,ProtocolOption protocolOption) {
        this.channelCreator = channelCreator;
        this.transport= TransportFactory.newTransport(protocolOption,channelCreator);
    }

    protected Destination getRemote(TP tp){return null;}

    protected abstract Future<Channel> proxy(Channel channel, NetLocation netLocation);

    protected Future<Channel> createChannel(EventLoop eventLoop,NetLocation netLocation) throws Exception {

        Destination destination = getRemote(netLocation.getTp());
        if (destination==null) {
            destination = new Destination(netLocation.getDest(),netLocation.getTp());
        }
        Promise<Channel> promise = eventLoop.newPromise();

        Async.toFuture(transport.createChannel(eventLoop,destination,netLocation.getSrc().isIpv4()? AddrType.IPV4:AddrType.IPV6))
                .compose(it->Async.toFuture(proxy(it,netLocation)))
                .onComplete(Async.futureCascadeCallback(promise));

        return promise;
    }

    @Override
    public Future<EndPoint> createEndPoint(ProxyContext proxyContext) throws Exception {
        NetLocation netLocation = proxyContext.getNetLocation();

        Promise<EndPoint> promise = proxyContext.getEventLoop().newPromise();
        Async.cascadeFailure(createChannel(proxyContext.getEventLoop(),proxyContext.getNetLocation()),promise,f->{
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
