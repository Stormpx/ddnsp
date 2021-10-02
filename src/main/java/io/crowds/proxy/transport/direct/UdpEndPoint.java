package io.crowds.proxy.transport.direct;

import io.crowds.proxy.EndPoint;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetSocketAddress;

public class UdpEndPoint implements EndPoint {

    private Channel channel;
    private InetSocketAddress recipient;

    public UdpEndPoint(Channel channel, InetSocketAddress recipient) {
        this.channel = channel;
        this.recipient = recipient;
    }

    @Override
    public void write(ByteBuf buf) {
        channel.writeAndFlush(new DatagramPacket(buf,recipient,null));
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public void close() {

    }
}
