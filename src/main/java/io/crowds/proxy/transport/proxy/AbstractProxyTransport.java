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

public abstract class AbstractProxyTransport<OPT extends ProtocolOption> implements ProxyTransport {
    private final static Logger logger= LoggerFactory.getLogger(AbstractProxyTransport.class);
    private final OPT protocolOption;
    private final String protocol;
    protected final ChannelCreator channelCreator;
    protected Transport transport;

    public AbstractProxyTransport(ChannelCreator channelCreator,OPT protocolOption) {
        this.channelCreator = channelCreator;
        this.protocolOption = protocolOption;
        this.transport= TransportFactory.newTransport(protocolOption,channelCreator);
        this.protocol= protocolOption.getProtocol();
    }

    public Transport getTransport() {
        return transport;
    }

    public AbstractProxyTransport setTransport(Transport transport) {
        this.transport = transport;
        return this;
    }

    public OPT getProtocolOption() {
        return protocolOption;
    }

    protected HandlerName handlerName(){
        return new HandlerName(protocol+"-"+getTag());
    }

    public Destination getRemote(TP tp){return null;}

    protected Future<Channel> proxy(Channel channel, NetLocation netLocation){
        return channel.eventLoop().newSucceededFuture(channel);
    }

    public Future<Channel> createChannel(EventLoop eventLoop, NetLocation netLocation) throws Exception {
        Destination destination = getRemote(netLocation.getTp());
        if (destination==null) {
            destination = new Destination(netLocation.getDst(),netLocation.getTp());
        }
        Promise<Channel> promise = eventLoop.newPromise();

        Async.toFuture(this.transport.openChannel(eventLoop,destination,netLocation.getSrc().isIpv4()? AddrType.IPV4:AddrType.IPV6))
             .compose(it->Async.toFuture(proxy(it,netLocation)))
             .onComplete(Async.futureCascadeCallback(promise));

        return promise;
    }


    @Override
    public String getTag() {
        return getProtocolOption().getName();
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
