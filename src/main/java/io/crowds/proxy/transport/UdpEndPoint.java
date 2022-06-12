package io.crowds.proxy.transport;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.UdpChannel;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class UdpEndPoint extends EndPoint {
    private Channel channel;
    private UdpChannel udpChannel;
    private NetAddr dest;

    public UdpEndPoint(Channel channel, NetAddr dest) {
        this.channel = channel;
        this.dest = dest;
    }

    public UdpEndPoint(UdpChannel channel, NetAddr dest) {
        this.udpChannel=channel;
        this.channel = channel.getChannel();
        this.dest=dest;
        channel.packetHandler(dest, this::fireBuf);
    }

    @Override
    public void write(Object msg) {
        if (!channel.isActive()){
            ReferenceCountUtil.safeRelease(msg);
            return;
        }
        if (msg instanceof DatagramPacket packet){
            channel.writeAndFlush(new DatagramPacket(packet.content(), dest.getAsInetAddr(),packet.sender()))
                    .addListener(f->{
                        if (!f.isSuccess()){
                            fireException(f.cause());
                        }
                    });
        }else if (msg instanceof ByteBuf){
            channel.writeAndFlush(new DatagramPacket((ByteBuf) msg,dest.getAsInetAddr(),null))
                    .addListener(f->{
                        if (!f.isSuccess()){
                            fireException(f.cause());
                        }
                    });
        }

    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public void close() {
        if (this.udpChannel!=null){
            this.udpChannel.packetHandler(dest,null);
        }
    }

    @Override
    public Future<Void> closeFuture() {
        return this.channel.closeFuture();
    }
}
