package io.crowds.proxy.transport;

import io.crowds.proxy.NetAddr;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;

public class UdpEndPoint extends EndPoint {
    private final Channel channel;
    private final UdpChannel udpChannel;
    private final NetAddr dest;

    public UdpEndPoint(Channel channel, NetAddr dest) {
        this.channel = channel;
        this.udpChannel = null;
        this.dest = dest;
    }

    public UdpEndPoint(UdpChannel channel, NetAddr dest) {
        this.channel = channel.getChannel();
        this.udpChannel=channel;
        this.dest=dest;
        channel.packetHandler(dest, buf->{
            fireBuf(buf);
            fireReadComplete();
        });
    }

    @Override
    public void write(Object msg) {
        if (!channel.isActive()){
            ReferenceCountUtil.safeRelease(msg);
            return;
        }
        if (msg instanceof DatagramPacket packet){
            channel.write(new DatagramPacket(packet.content(), dest.getAsInetAddr(),packet.sender()))
                    .addListener(f->{
                        if (!f.isSuccess()){
                            fireException(f.cause());
                        }
                    });
        }else if (msg instanceof ByteBuf){
            channel.write(new DatagramPacket((ByteBuf) msg,dest.getAsInetAddr(),null))
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
