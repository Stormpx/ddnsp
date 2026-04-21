package io.crowds.proxy.transport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelOutputShutdownEvent;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.DuplexChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class TcpEndPoint extends EndPoint {

    private final Channel channel;
    private final Collection<Class<? extends Throwable>> ignoreExceptions;
    private final boolean closeOnException;
    private volatile boolean closed;
    private Shutdown shutdown;

    public TcpEndPoint(Channel channel) {
        this.channel = channel;
        this.ignoreExceptions = List.of(Http2Exception.class);
        this.closeOnException = false;
        setAutoRead(false);
        init();
    }

    @Override
    public void setAutoRead(boolean autoRead) {
        if (autoRead&&shutdown==Shutdown.INPUT){
            return;
        }
        super.setAutoRead(autoRead);
    }

    private void init(){
        this.channel.pipeline().addLast(new ChannelDuplexHandler() {
            private ByteBuf cumulation;
            private final ByteToMessageDecoder.Cumulator cumulator = ByteToMessageDecoder.COMPOSITE_CUMULATOR;
            @Override
            public void read(ChannelHandlerContext ctx) throws Exception {
                ByteBuf buf = cumulation;
                if (cumulation!=null){
                    cumulation = null;
                    ctx.executor().submit(()->{
                        fireBuf(buf);
                        fireReadComplete();
                    });
                }
                super.read(ctx);
            }

            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
                boolean writable = ctx.channel().isWritable();
                if (!writable){
                    ctx.flush();
                    if (ctx.channel().isWritable()){
                        return;
                    }
                }
                fireWriteable(writable);
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                closed=true;
                super.channelInactive(ctx);
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (!channel.isActive()){
                    ReferenceCountUtil.safeRelease(msg);
                    return;
                }
                if (!(msg instanceof ByteBuf buf)){
                    ctx.fireChannelRead(msg);
                    return;
                }

                if (!channel.config().isAutoRead()){
                    cumulation = cumulator.cumulate(ctx.alloc(), Objects.requireNonNullElse(cumulation, Unpooled.EMPTY_BUFFER),buf);
                }else {
                    fireBuf(buf);
                }
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                fireReadComplete();
                super.channelReadComplete(ctx);
            }

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                var shutdown = TcpEndPoint.this.shutdown;
                if (evt instanceof ChannelInputShutdownEvent){
                    logger.info("shutdownInput Event");
                    fireShutdown(TcpEndPoint.this.shutdown = Shutdown.INPUT);
                }else if (evt instanceof ChannelOutputShutdownEvent){
                    logger.info("shutdownOutput Event");
                    fireShutdown(TcpEndPoint.this.shutdown = Shutdown.OUTPUT);
                }
                if (shutdown!=null && shutdown != TcpEndPoint.this.shutdown){
                    ctx.close();
                }
                super.userEventTriggered(ctx, evt);
            }

            private boolean ignore(Throwable cause){
                for (Class<? extends Throwable> exKlass : ignoreExceptions) {
                    if (exKlass.isInstance(cause)){
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                if (ignore(cause)){
                    ctx.fireExceptionCaught(cause);
                }else{
                    fireException(cause);
                    if (closeOnException){
                        ctx.close();
                    }
                }

            }
        });

    }

    private void doWrite(Object msg){
        if (this.shutdown==Shutdown.OUTPUT){
            logger.error("Attempt to write message after shutdownOutput is scheduled");
            ReferenceCountUtil.safeRelease(msg);
            return;
        }
        if (msg instanceof DatagramPacket packet) {
            msg=packet.content();
        }
        channel.write(msg)
                .addListener(f->{
                    if (!f.isSuccess()){
                        fireException(f.cause());
                    }
                    ChannelOutboundBuffer buffer = channel.unsafe().outboundBuffer();
                    if (shutdown!=null) {
                        logger.info("shutdown:{} outbuffer_null: {} empty:{}",this.shutdown,buffer==null, buffer != null && buffer.isEmpty());
                    }
                    if (this.shutdown==Shutdown.OUTPUT && (buffer==null||buffer.isEmpty())){
                        logger.error("shutdown output after all msg flushed");
                        shutdown(Shutdown.OUTPUT);
                    }
                });
    }

    @Override
    public void write(Object msg) {
        if (!channel.isActive()||closed){
            ReferenceCountUtil.safeRelease(msg);
            return;
        }
        EventLoop eventLoop = channel.eventLoop();
        if (eventLoop.inEventLoop()){
            doWrite(msg);
        }else{
            eventLoop.execute(()-> doWrite(msg));
        }
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public void shutdown(Shutdown shutdown) {
        EventLoop eventLoop = channel.eventLoop();
        if (eventLoop.inEventLoop()){
            if (this.shutdown !=null && this.shutdown != shutdown){
                logger.info("close channel");
                close();
            }else {
                this.shutdown = shutdown;
                if (channel instanceof DuplexChannel duplex){
                    switch (shutdown){
                        case INPUT -> duplex.shutdownInput();
                        case OUTPUT -> {
                            ChannelOutboundBuffer buffer = channel.unsafe().outboundBuffer();
                            if (buffer==null)
                                return;
                            if (buffer.isEmpty()){
                                logger.info("shutdown output");
                                duplex.shutdownOutput();
                            }else{
                                logger.info("nonono");
                                flush();
                            }
                        }
                    }
                }else{
                    close();
                }
            }
        }else{
            eventLoop.execute(()-> shutdown(shutdown));
        }

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
