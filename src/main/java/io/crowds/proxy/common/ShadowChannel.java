package io.crowds.proxy.common;

import io.crowds.util.Async;
import io.netty.channel.*;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.time.format.DateTimeFormatter;

/**
 * the shadow of other Channel. but with clean channelPipeline
 * only focus on data transfer. other Channel operation direct delegate to the master channel
 */
public class ShadowChannel extends AbstractChannel {

    private final Channel master;
    private final ChannelConfig config;
    private final ChannelMetadata metadata;
    private volatile boolean active=true;

    private SocketAddress localAddress;

    ShadowChannel(Channel master) {
        super(null);
        this.master=master;
        this.metadata=new ChannelMetadata(false);
        this.config=new DefaultChannelConfig(this);
        this.master.pipeline().addLast("shadow-pipeline-handler",new BridgingPipeLineHandler(this));
        this.master.closeFuture().addListener(_->close());
        this.pipeline().addFirst("internal-head-context",new InternalHeadContext());
    }

    public static ShadowChannel getChannel(Channel channel){
        var handler =channel.pipeline().get(BridgingPipeLineHandler.class);
        return handler != null ? handler.shadowChannel : null;
    }

    public static ShadowChannel getDeepestChannel(Channel channel){
        ShadowChannel shadowChannel = getChannel(channel);
        while (shadowChannel!=null){
            ShadowChannel nextChannel = getChannel(shadowChannel);
            if (nextChannel==null){
                break;
            }
            shadowChannel=nextChannel;
        }
        return shadowChannel;
    }

    public static boolean isAlreadyExists(Channel channel){
        return channel.pipeline().get(BridgingPipeLineHandler.class)!=null;
    }

    public static Future<ShadowChannel> shadow(Channel channel){
        var isShadowAlreadyExists = isAlreadyExists(channel);
        if (isShadowAlreadyExists){
            return channel.eventLoop().newFailedFuture(new IllegalStateException("Master channel already shadowed"));
        }
        Promise<ShadowChannel> promise = channel.eventLoop().newPromise();
        ShadowChannel shadowChannel = new ShadowChannel(channel);
        if (channel.eventLoop()!=null){
            Async.cascadeFailure(channel.eventLoop().register(shadowChannel),promise,_->promise.trySuccess(shadowChannel));
        }else{
            promise.trySuccess(shadowChannel);
        }
        return promise;
    }

    public Channel getMaster(){
        return master;
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new ShadowUnsafe();
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return master.eventLoop()==loop;
    }

    @Override
    protected SocketAddress localAddress0() {
        return master.localAddress();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return master.remoteAddress();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        //do nothing
        this.localAddress=localAddress;
    }
    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        ChannelPromise channelPromise = newPromise();
        this.localAddress=localAddress;
        Async.cascadeFailure(pipeline().bind(localAddress),channelPromise,f->master.bind(this.localAddress,channelPromise));
        return channelPromise;
    }
    @Override
    protected void doDisconnect() throws Exception {
        if (!master.isActive()){
            this.active=false;
        }
    }

    @Override
    public ChannelFuture disconnect() {
        ChannelPromise channelPromise = newPromise();
        Async.cascadeFailure(master.disconnect(),channelPromise,f->pipeline().disconnect(channelPromise));
        return channelPromise;
    }

    @Override
    protected void doClose() throws Exception {
        this.active=false;
    }
    @Override
    public ChannelFuture close() {
        ChannelPromise channelPromise = newPromise();
        Async.cascadeFailure(master.close(),channelPromise,f->pipeline().close(channelPromise));
        return channelPromise;
    }

    @Override
    protected void doBeginRead() throws Exception {
        master.read();
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        while (true){
            Object current = in.current();
            if (current==null){
                break;
            }
            if (!master.isOpen()){
                in.remove(new ClosedChannelException());
            }else{
                if (current instanceof ReferenceCounted counted){
                    counted.retain();
                }
                master.write(current);
                in.remove();
            }
        }
        master.flush();
    }


    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return active;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public ChannelMetadata metadata() {
        return metadata;
    }

    class ShadowUnsafe extends AbstractUnsafe{
        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            master.connect(remoteAddress,localAddress,promise);
        }

    }

    class InternalHeadContext extends ChannelOutboundHandlerAdapter{

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            master.write(msg).addListener(Async.cascade(promise));
        }

        @Override
        public void flush(ChannelHandlerContext ctx) throws Exception {
            master.flush();
        }
    }

    class BridgingPipeLineHandler extends ChannelInboundHandlerAdapter{
        private ShadowChannel shadowChannel;

        public BridgingPipeLineHandler(ShadowChannel shadowChannel) {
            this.shadowChannel = shadowChannel;
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            if (isRegistered()){
                deregister();
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (isOpen()) {
                pipeline().fireChannelRead(msg);
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            if (isOpen()) {
                pipeline().fireChannelReadComplete();
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            pipeline().fireUserEventTriggered(evt);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            pipeline().fireChannelWritabilityChanged();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            pipeline().fireExceptionCaught(cause);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            pipeline().fireChannelInactive();
        }
    }
}
