package io.crowds.proxy.transport;

import io.crowds.util.AddrType;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;

public abstract class AbstractTransport implements Transport{

    protected abstract Future<Channel> doCreateChannel(EventLoop eventLoop, Destination dest, AddrType preferType) throws Exception;
    @Override
    public Future<Channel> openChannel(EventLoop eventLoop, Destination dest, AddrType preferType, Transport delegate) throws Exception {
        if (this!=delegate){
            delegate.openChannel(eventLoop, dest, preferType, delegate);
        }
        return doCreateChannel(eventLoop, dest, preferType);
    }
}
