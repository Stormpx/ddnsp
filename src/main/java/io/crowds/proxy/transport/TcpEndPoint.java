package io.crowds.proxy.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;

import java.util.function.Consumer;

public class TcpEndPoint extends EndPoint {

    private final Channel channel;
    private boolean closed;

    public TcpEndPoint(Channel channel) {
        this.channel = channel;
        setAutoRead(false);
        init();
    }

    private void init(){
        this.channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>(false) {
            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
                fireWriteable(ctx.channel().isWritable());
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                closed=true;
                super.channelInactive(ctx);
            }

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                if (!channel.isActive()){
                    ReferenceCountUtil.safeRelease(msg);
                    return;
                }
                fireBuf(msg);
            }
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                fireException(cause);
            }
        });

    }




    @Override
    public void write(Object msg) {
        if (!channel.isActive()||closed){
            ReferenceCountUtil.safeRelease(msg);
            return;
        }
        if (msg instanceof DatagramPacket packet) {
            msg=packet.content();
        }
        channel.writeAndFlush(msg)
                .addListener(f->{
                    if (!f.isSuccess()){
                        fireException(f.cause());
                    }
                });;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public void close() {
        if (channel.isActive()) {
            closed=true;
            channel.close();
        }
    }

    @Override
    public Future<Void> closeFuture() {
        return this.channel.closeFuture();
    }

}
