package io.crowds.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;


public interface EndPoint {


    void write(ByteBuf buf);

    Channel channel();

    void close();
}
