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
    private final static Object NULL = new Object();

    private final Channel channel;
    private final InetSocketAddress src;
    private final boolean closeIfEmpty;

    private final Map<Object, Consumer<DatagramPacket>> handlers=new ConcurrentHashMap<>();



    public UdpChannel(Channel channel,InetSocketAddress src,boolean closeIfEmpty) {
        this.channel = channel;
        this.src=src;
        this.closeIfEmpty=closeIfEmpty;
        this.channel.pipeline().addLast(this);
    }

    private void updateHandlers(Object key,Consumer<DatagramPacket> bufferHandler){
        if (bufferHandler==null){
            handlers.remove(key);
            if (closeIfEmpty){
                if (handlers.isEmpty()){
                    channel.close();
                }
            }
        }else {
            handlers.put(key, bufferHandler);
        }
    }

    public UdpChannel fallbackHandler(Consumer<DatagramPacket> bufferHandler) {
        updateHandlers(NULL,bufferHandler);
        return this;
    }

    public UdpChannel packetHandler(NetAddr netAddr,Consumer<DatagramPacket> bufferHandler){
        updateHandlers(netAddr.getAsInetAddr(),bufferHandler);
        return this;
    }


    public Channel getChannel(){
        return channel;
    }



    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof DatagramPacket packet){
            InetSocketAddress address = packet.sender();
            Consumer<DatagramPacket> handler = handlers.get(address==null?NULL:address);
            if (handler!=null) {
                handler.accept(new DatagramPacket(packet.content(),src,address));
            }
            return;
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (logger.isErrorEnabled()) {
            logger.error("udp channel caught exception", cause);
        }else{
            logger.warn("udp channel caught exception: {}", cause.getMessage());
        }
        ctx.close();
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
