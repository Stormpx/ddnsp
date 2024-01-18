package io.crowds.proxy.transport.proxy.chain;

import io.crowds.proxy.NetLocation;
import io.crowds.proxy.common.ShadowChannel;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.Transport;
import io.crowds.proxy.transport.proxy.AbstractProxyTransport;
import io.crowds.util.AddrType;
import io.crowds.util.Async;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class ChainContext {
    private final EventLoop eventLoop;
    private final NetLocation netLocation;

    private final Transport chainOutTransport;
    //the four-tuple is different each time a proxy is used, we need to have access to the original four-tuple when creating a proxy chain.
    //Therefore, we reinitialize the transport every time a channel is created.
    private ProxyTransportTransport chainedTransport;



    public ChainContext(EventLoop eventLoop, NetLocation netLocation, List<AbstractProxyTransport> proxyTransports,Transport transport) {
        Objects.requireNonNull(eventLoop);
        Objects.requireNonNull(netLocation);
        Objects.requireNonNull(proxyTransports);
        this.eventLoop = eventLoop;
        this.netLocation = netLocation;
        this.chainOutTransport =transport;
        initChain(proxyTransports);
    }

    private void initChain(List<AbstractProxyTransport> proxyTransports){
        Iterator<AbstractProxyTransport> iterator = proxyTransports.iterator();
        ProxyTransportTransport transport = null;
        while (iterator.hasNext()){
            AbstractProxyTransport proxyTransport = iterator.next();
            transport=new ProxyTransportTransport(proxyTransport,transport);
        }
        if (transport==null){
            throw new IllegalArgumentException("proxyTransportList is empty");
        }
        this.chainedTransport=transport;
    }

    public Future<Channel> createChannel() throws Exception {
        assert chainedTransport!=null;
        return chainedTransport.openChannel(eventLoop,new Destination(netLocation),netLocation.getSrc().isIpv4()?AddrType.IPV4:AddrType.IPV6);
    }

    class ProxyTransportTransport implements Transport{
        private final AbstractProxyTransport proxyTransport;
        private final Transport head;
        public ProxyTransportTransport(AbstractProxyTransport proxyTransport, Transport head) {
            this.proxyTransport = proxyTransport;
            this.head = head;
        }

        @Override
        public Future<Channel> openChannel(EventLoop eventLoop, Destination dest, AddrType preferType) throws Exception {
            Promise<Channel> promise = eventLoop.newPromise();
            Future<Channel> channelFuture;
            NetLocation nextLocation = new NetLocation(netLocation.getSrc(), dest.addr(), dest.tp());
            if (head ==null){
                //the last ProxyTransportTransport. which is first node.
                if (chainOutTransport!=null) {
                    channelFuture = proxyTransport.createChannel(eventLoop, nextLocation, chainOutTransport);
                }else{
                    channelFuture = proxyTransport.createChannel(eventLoop, nextLocation);
                }
            }else{
                channelFuture = proxyTransport.createChannel(eventLoop, nextLocation, head);
            }

            Async.cascadeFailure(channelFuture,promise,f-> ShadowChannel.shadow(f.get()).addListener(Async.cascade(promise)));
            return promise;
        }

        @Override
        public Future<Channel> openChannel(EventLoop eventLoop, Destination dest, AddrType preferType, Transport delegate) throws Exception {
            if (delegate != this) {
                throw new UnsupportedOperationException("Chained transport does not support delegating to other transports");
            }
            return openChannel(eventLoop, dest, preferType);
        }
    }
}
