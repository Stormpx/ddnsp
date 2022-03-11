package io.crowds.proxy.transport;

import io.crowds.proxy.NetAddr;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.timeout.IdleStateEvent;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;


public class UdpChannel  extends ChannelInboundHandlerAdapter {


    private DatagramChannel datagramChannel;

    private volatile Consumer<DatagramPacket> fallbackPacketHandler;

    private Map<InetSocketAddress, Consumer<DatagramPacket>> handlers=new ConcurrentHashMap<>();


    public UdpChannel(DatagramChannel datagramChannel) {
        this.datagramChannel = datagramChannel;
        this.datagramChannel.pipeline().addLast(this);
    }

    public UdpChannel fallbackHandler(Consumer<DatagramPacket> bufferHandler) {
        this.fallbackPacketHandler = bufferHandler;
        return this;
    }

    public UdpChannel packetHandler(NetAddr netAddr,Consumer<DatagramPacket> bufferHandler){
        handlers.put(netAddr.getAsInetAddr(),bufferHandler);

        return this;
    }

    public DatagramChannel getDatagramChannel() {
        return datagramChannel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof DatagramPacket packet){
            InetSocketAddress address = packet.sender();
            Consumer<DatagramPacket> handler = handlers.getOrDefault(address,this.fallbackPacketHandler);
            if (handler!=null)
                handler.accept(packet);

            return;
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent){
            datagramChannel.close();
        }else
            super.userEventTriggered(ctx, evt);
    }

    @Override
    public String toString() {
        return datagramChannel.localAddress().toString();
    }
}
