package io.crowds.proxy.transport.proxy.chain;

import io.crowds.proxy.NetAddr;
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

public class ProxyTransportTransport implements Transport {
    private final NetAddr src;
    private final AbstractProxyTransport<?> proxyTransport;
    public ProxyTransportTransport(NetAddr src, AbstractProxyTransport<?> proxyTransport) {
        this.src = src;
        this.proxyTransport = proxyTransport;
    }

    @Override
    public Future<Channel> openChannel(EventLoop eventLoop, Destination dest, AddrType preferType) throws Exception {
        Promise<Channel> promise = eventLoop.newPromise();
        NetLocation nextLocation = new NetLocation(src, dest.addr(), dest.tp());
        Future<Channel> channelFuture = proxyTransport.createChannel(eventLoop, nextLocation);

        Async.cascadeFailure(channelFuture,promise, f-> ShadowChannel.shadow(f.get()).addListener(Async.cascade(promise)));
        return promise;
    }

    @Override
    public Future<Channel> openChannel(EventLoop eventLoop, Destination dest, AddrType preferType, Transport delegate) throws Exception {
        if (delegate != this) {
            throw new UnsupportedOperationException("Chained transport delegating other transport is not support");
        }
        return openChannel(eventLoop, dest, preferType);
    }
}
