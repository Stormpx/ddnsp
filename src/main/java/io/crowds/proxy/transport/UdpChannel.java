package io.crowds.proxy.transport;

import io.crowds.proxy.NetAddr;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;


public class UdpChannel  extends ChannelInboundHandlerAdapter {
    private final static Logger logger= LoggerFactory.getLogger(UdpChannel.class);

    private Channel channel;

    private Consumer<DatagramPacket> fallbackPacketHandler;

    private Map<InetSocketAddress, Consumer<DatagramPacket>> handlers=new ConcurrentHashMap<>();



    public UdpChannel(Channel channel) {
        this.channel = channel;
        this.channel.pipeline().addLast(this);
    }


    public UdpChannel fallbackHandler(Consumer<DatagramPacket> bufferHandler) {
        this.fallbackPacketHandler = bufferHandler;
        return this;
    }

    public UdpChannel packetHandler(NetAddr netAddr,Consumer<DatagramPacket> bufferHandler){
        if (bufferHandler==null){
            handlers.remove(netAddr.getAsInetAddr());
        }else {
            handlers.put(netAddr.getAsInetAddr(), bufferHandler);
        }
        return this;
    }


    public Channel getChannel(){
        return channel;
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

//    @Override
//    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
//        if (evt instanceof IdleStateEvent){
//            channel.close();
//        }else
//            super.userEventTriggered(ctx, evt);
//    }

    @Override
    public String toString() {
        return channel.localAddress().toString();
    }
}
