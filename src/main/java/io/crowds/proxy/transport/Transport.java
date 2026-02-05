package io.crowds.proxy.transport;

import io.crowds.util.AddrType;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;

import java.util.Objects;
import java.util.function.Supplier;

public interface Transport {

    default Future<Channel> openChannel(EventLoop eventLoop, Destination dest, AddrType preferType) throws Exception{
        return openChannel(eventLoop,dest,preferType,null);
    }

    Future<Channel> openChannel(EventLoop eventLoop, Destination dest, AddrType preferType, Transport delegate) throws Exception;


    static Transport compose(Transport transport, Transport delegate){
        return new ComposedTransport(transport,delegate);
    }
    static Transport provider(Supplier<Transport> transportProvider){
        Objects.requireNonNull(transportProvider);
        return (eventLoop, dest, preferType, delegate) ->
                transportProvider.get().openChannel(eventLoop, dest, preferType,delegate);
    }

}
