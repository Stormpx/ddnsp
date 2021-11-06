package io.crowds.proxy.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.function.Consumer;


public class UdpChannel  extends ChannelInboundHandlerAdapter {


    private DatagramChannel datagramChannel;
    private Consumer<ByteBuf> bufferHandler;

    public UdpChannel(DatagramChannel datagramChannel) {
        this.datagramChannel = datagramChannel;
        this.datagramChannel.pipeline().addLast(this);
    }

    public UdpChannel bufferHandler(Consumer<ByteBuf> bufferHandler) {
        this.bufferHandler = bufferHandler;
        return this;
    }

    public DatagramChannel getDatagramChannel() {
        return datagramChannel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (bufferHandler!=null&&msg instanceof DatagramPacket){
            bufferHandler.accept(((DatagramPacket) msg).content());
            return;
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent){
            ctx.close();
        }else
            super.userEventTriggered(ctx, evt);
    }
}
