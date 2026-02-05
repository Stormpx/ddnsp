package io.crowds.proxy.transport;

import io.crowds.util.AddrType;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;

public record ComposedTransport(Transport transport, Transport delegate) implements Transport {


    @Override
    public Future<Channel> openChannel(EventLoop eventLoop, Destination dest, AddrType preferType, Transport delegate) throws Exception {
        if (delegate != null && delegate != this) {
            return transport.openChannel(eventLoop, dest, preferType, new ComposedTransport(this.delegate, delegate));
        }
        return transport.openChannel(eventLoop, dest, preferType, this.delegate);
    }
}
