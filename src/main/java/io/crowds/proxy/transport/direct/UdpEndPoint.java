package io.crowds.proxy.transport.direct;

import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.UdpChannel;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.concurrent.Future;

import java.net.InetSocketAddress;

public class UdpEndPoint extends EndPoint {

    private Channel channel;
    private InetSocketAddress recipient;


    public UdpEndPoint(Channel channel, InetSocketAddress recipient) {
        this.channel = channel;
        this.recipient = recipient;
    }

    public UdpEndPoint(UdpChannel channel, InetSocketAddress recipient) {
        this.channel = channel.getDatagramChannel();
        this.recipient = recipient;
        channel.bufferHandler(this::fireBuf);
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

    @Override
    public Future<Void> closeFuture() {
        return this.channel.closeFuture();
    }
}
