package io.crowds.proxy.transport;

import io.crowds.util.AddrType;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;

public interface Transport {

    default Future<Channel> openChannel(EventLoop eventLoop, Destination dest, AddrType preferType) throws Exception{
        return openChannel(eventLoop,dest,preferType,null);
    }

    Future<Channel> openChannel(EventLoop eventLoop, Destination dest, AddrType preferType, Transport delegate) throws Exception;

}
