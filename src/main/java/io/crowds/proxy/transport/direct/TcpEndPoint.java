package io.crowds.proxy.transport.direct;

import io.crowds.proxy.EndPoint;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

public class TcpEndPoint implements EndPoint {

    private Channel channel;

    public TcpEndPoint(Channel channel) {
        this.channel = channel;
    }

    @Override
    public void write(ByteBuf buf) {
        channel.writeAndFlush(buf);
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public void close() {
        if (channel.isActive())
            channel.close();
    }

}
