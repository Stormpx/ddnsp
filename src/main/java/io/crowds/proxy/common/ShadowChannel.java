package io.crowds.proxy.common;

import io.crowds.util.Async;
import io.netty.channel.*;
import io.netty.util.DefaultAttributeMap;

import java.net.SocketAddress;

/**
 * Shadow of another Channel, but with a clean ChannelPipeline.
 * Only focuses on data transfer. Other Channel operations are directly delegated to the original channel.
 */
public class ShadowChannel extends DefaultAttributeMap implements Channel {
    private final Channel parent;
    private final ChannelPipeline pipeline;
    private final ShadowUnsafe unsafe;

    private ShadowChannel(Channel parent) {
        this.parent = parent;
        this.unsafe = new ShadowUnsafe();
        this.pipeline = new DefaultChannelPipeline(this){};
        this.parent.pipeline().addLast("shadow-pipeline-handler",new BridgingPipelineHandler());
        if (parent.isRegistered()){
            pipeline.fireChannelRegistered();
        }
    }

    public static ShadowChannel getChannel(Channel channel){
        var handler =channel.pipeline().get(BridgingPipelineHandler.class);
        return handler != null ? handler.channel() : null;
    }

    public static boolean isAlreadyExists(Channel channel){
        return channel.pipeline().get(BridgingPipelineHandler.class)!=null;
    }

    public static ShadowChannel shadow(Channel channel){
        var isShadowAlreadyExists = isAlreadyExists(channel);
        if (isShadowAlreadyExists){
            throw new IllegalStateException("Channel already shadowed");
        }
        return new ShadowChannel(channel);
    }

    private final class ShadowUnsafe implements Unsafe{

        @Override
        public RecvByteBufAllocator.Handle recvBufAllocHandle() {
            return parent.unsafe().recvBufAllocHandle();
        }

        @Override
        public SocketAddress localAddress() {
            return parent.unsafe().localAddress();
        }

        @Override
        public SocketAddress remoteAddress() {
            return parent.unsafe().remoteAddress();
        }

        @Override
        public void register(EventLoop eventLoop, ChannelPromise promise) {
            eventLoop.register(parent).addListener(Async.cascade(promise));
        }

        @Override
        public void bind(SocketAddress localAddress, ChannelPromise promise) {
            parent.bind(localAddress).addListener(Async.cascade(promise));
        }

        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            parent.connect(remoteAddress,localAddress).addListener(Async.cascade(promise));
        }

        @Override
        public void disconnect(ChannelPromise promise) {
            parent.disconnect().addListener(Async.cascade(promise));
        }

        @Override
        public void close(ChannelPromise promise) {
            parent.close().addListener(Async.cascade(promise));
        }

        @Override
        public void closeForcibly() {
            parent.unsafe().closeForcibly();
        }

        @Override
        public void deregister(ChannelPromise promise) {
            parent.deregister().addListener(Async.cascade(promise));
        }

        @Override
        public void beginRead() {
            parent.read();
        }

        @Override
        public void write(Object msg, ChannelPromise promise) {
            parent.write(msg).addListener(Async.cascade(promise));
        }

        @Override
        public void flush() {
            parent.flush();
        }

        @Override
        public ChannelPromise voidPromise() {
            return new DefaultChannelPromise(ShadowChannel.this);
        }

        @Override
        public ChannelOutboundBuffer outboundBuffer() {
            return parent.unsafe().outboundBuffer();
        }
    }

    private final class BridgingPipelineHandler extends ChannelInboundHandlerAdapter{


        ShadowChannel channel(){
            return ShadowChannel.this;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            pipeline.fireChannelActive();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            pipeline.fireChannelInactive();
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            pipeline.fireChannelRegistered();
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            pipeline.fireChannelUnregistered();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            pipeline.fireChannelRead(msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            pipeline.fireChannelReadComplete();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            pipeline.fireUserEventTriggered(evt);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            pipeline.fireChannelWritabilityChanged();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            pipeline.fireExceptionCaught(cause);
        }

    }


    @Override
    public ChannelId id() {
        return parent.id();
    }

    @Override
    public EventLoop eventLoop() {
        return parent.eventLoop();
    }

    @Override
    public Channel parent() {
        return parent;
    }

    @Override
    public ChannelConfig config() {
        return parent.config();
    }

    @Override
    public boolean isOpen() {
        return parent.isOpen();
    }

    @Override
    public boolean isRegistered() {
        return parent.isRegistered();
    }

    @Override
    public boolean isActive() {
        return parent.isActive();
    }

    @Override
    public ChannelMetadata metadata() {
        return parent.metadata();
    }

    @Override
    public SocketAddress localAddress() {
        return parent.localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return parent.remoteAddress();
    }

    @Override
    public ChannelFuture closeFuture() {
        return parent.closeFuture();
    }

    @Override
    public Unsafe unsafe() {
        return unsafe;
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    @Override
    public int compareTo(Channel o) {
        return parent.compareTo(o);
    }
}
