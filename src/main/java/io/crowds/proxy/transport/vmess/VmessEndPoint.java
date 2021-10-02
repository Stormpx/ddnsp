package io.crowds.proxy.transport.vmess;

import io.crowds.proxy.EndPoint;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

public class VmessEndPoint implements EndPoint {

    private Channel channel;

    public VmessEndPoint(Channel channel) {
        this.channel = channel;
    }

    @Override
    public void write(ByteBuf buf) {

    }

    @Override
    public Channel channel() {
        return null;
    }

    @Override
    public void close() {

    }
}
