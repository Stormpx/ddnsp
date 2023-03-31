package io.crowds.proxy.transport;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.TP;
import io.crowds.util.AddrType;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;

public interface Transport {

    Future<Channel> createChannel(EventLoop eventLoop, Destination dest, AddrType preferType) throws Exception;

}
