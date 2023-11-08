package io.crowds.proxy.transport.proxy;

import io.crowds.proxy.*;
import io.crowds.proxy.common.HandlerName;
import io.crowds.proxy.transport.*;
import io.crowds.util.AddrType;
import io.crowds.util.Async;
import io.netty.channel.*;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractProxyTransport implements ProxyTransport {
    private final static Logger logger= LoggerFactory.getLogger(AbstractProxyTransport.class);
    protected ChannelCreator channelCreator;
    protected Transport transport;
    private String protocol;
    public AbstractProxyTransport(ChannelCreator channelCreator,ProtocolOption protocolOption) {
        this.channelCreator = channelCreator;
        this.transport= TransportFactory.newTransport(protocolOption,channelCreator);
        this.protocol= protocolOption.getProtocol();
    }

    protected HandlerName handlerName(){
        return new HandlerName(STR."\{protocol}-\{getTag()}");
    }

    protected String handlerName(String name){
        return STR."\{protocol}-\{getTag()}-\{name}";
    }

    protected Destination getRemote(TP tp){return null;}

    protected abstract Future<Channel> proxy(Channel channel, NetLocation netLocation);

    public Future<Channel> createChannel(EventLoop eventLoop, NetLocation netLocation) throws Exception {
        return createChannel(eventLoop,netLocation,transport);
    }

    public Future<Channel> createChannel(EventLoop eventLoop, NetLocation netLocation, Transport delegate) throws Exception {

        Destination destination = getRemote(netLocation.getTp());
        if (destination==null) {
            destination = new Destination(netLocation.getDst(),netLocation.getTp());
        }
        Promise<Channel> promise = eventLoop.newPromise();

        Async.toFuture(this.transport.createChannel(eventLoop,destination,netLocation.getSrc().isIpv4()? AddrType.IPV4:AddrType.IPV6,delegate))
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
                UdpChannel udpChannel = new UdpChannel(ch,netLocation.getSrc().getAsInetAddr(),true);
                if (proxyContext.fallbackPacketHandler()!=null)
                    udpChannel.fallbackHandler(proxyContext.fallbackPacketHandler());
                promise.trySuccess(new UdpEndPoint(udpChannel,netLocation.getDst()));
            }
        });
        return promise;
    }
}
