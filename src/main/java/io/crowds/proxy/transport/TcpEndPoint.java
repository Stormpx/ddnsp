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
    private Consumer<Throwable> throwableHandler;

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
            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                if (!channel.isActive()){
                    ReferenceCountUtil.safeRelease(msg);
                    return;
                }
                fireBuf(msg);
            }
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
//                logger.info("src {} caught exception :{}",ctx.channel().remoteAddress(),cause.getMessage());
                if (throwableHandler!=null)
                    throwableHandler.accept(cause);
            }

//            @Override
//            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
//                if (evt instanceof IdleStateEvent){
//                    close();
////                    handleThrowable(new RuntimeException("connect idle timeout"));
//                }else
//                    super.userEventTriggered(ctx, evt);
//            }
        });
    }


    public TcpEndPoint exceptionHandler(Consumer<Throwable> throwableHandler) {
        this.throwableHandler = throwableHandler;
        return this;
    }


    @Override
    public void write(Object msg) {
        if (!channel.isActive()){
            ReferenceCountUtil.safeRelease(msg);
            return;
        }
        if (msg instanceof DatagramPacket packet) {
            msg=packet.content();
        }
        channel.writeAndFlush(msg);
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public void close() {
        if (channel.isActive())
            channel.close();
    }

    @Override
    public Future<Void> closeFuture() {
        return this.channel.closeFuture();
    }

}
