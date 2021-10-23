package io.crowds.proxy.transport.vmess;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.EndPoint;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.TimeoutException;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class VmessEndPoint extends EndPoint {
    private final static Logger logger= LoggerFactory.getLogger(VmessEndPoint.class);


    private NetLocation netLocation;
    private VmessOption vmessOption;
    private ChannelCreator channelCreator;

    private Channel channel;
    private Promise<EndPoint> promise;
    private Promise<Void> closePromise;




    public VmessEndPoint(NetLocation netLocation, VmessOption vmessOption, ChannelCreator channelCreator) {
        this.netLocation = netLocation;
        this.vmessOption = vmessOption;
        this.channelCreator = channelCreator;
        this.promise = channelCreator.getEventLoopGroup().next().newPromise();
        this.closePromise=channelCreator.getEventLoopGroup().next().newPromise();
    }

    public Future<EndPoint> init(){

        var cf=channelCreator.createTcpChannel(vmessOption.getAddress(), new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline()
                        .addLast(new IdleStateHandler(0,0,vmessOption.getConnIdle()))
                        .addLast(new VmessMessageCodec())
                        .addLast(new VmessChannelHandler());
            }
        });
        cf.addListener(future -> {
            if (!future.isSuccess()){
                promise.tryFailure(future.cause());
                return;
            }
            this.channel=cf.channel();
            handleResponse();
        });
        return promise;
    }

    @Override
    public void write(ByteBuf buf) {
        channel.writeAndFlush(buf)
            .addListener(future -> {
                if (!future.isSuccess()){
                    handleThrowable(future.cause());
                }
            });
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public void close() {
        if (this.channel.isActive()) {
            channel.writeAndFlush(Unpooled.EMPTY_BUFFER);
            channel.close();
        }
    }

    @Override
    public Future<Void> closeFuture() {
        return this.closePromise;
    }

    void tryActive(){
        this.channel.writeAndFlush(Unpooled.EMPTY_BUFFER);
    }

    private void fireClose() {
        this.closePromise.trySuccess(null);
    }

    private void handleResponse(){
        this.promise.trySuccess(this);
    }

    private void handleDynamicCmd(VmessDynamicPortCmd msg){
        close();

    }

    private void handleThrowable(Throwable cause){
        if (!promise.isDone()){
            promise.tryFailure(cause);
        }else{
            logger.info("connection close cause: {}",cause.getMessage());
            close();
        }
    }


    public class VmessChannelHandler extends ChannelDuplexHandler {


        private void handshake(ChannelHandlerContext ctx){
            NetAddr dest = netLocation.getDest();

            User user = vmessOption.getUser();

            VmessRequest request = new VmessRequest( Set.of(Option.CHUNK_STREAM,Option.CHUNK_MASKING), netLocation.getTp(), dest);
            request.setUser(user.randomUser());
            request.setSecurity(vmessOption.getSecurity());

            ctx.writeAndFlush(request)
                .addListener(future -> {
                    if (!future.isSuccess()) {
                       handleThrowable(future.cause());
                    }
                });

        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {

            handshake(ctx);
            super.channelActive(ctx);
        }


        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf)
                fireBuf((ByteBuf) msg);
            if (msg instanceof VmessDynamicPortCmd)
                handleDynamicCmd((VmessDynamicPortCmd)msg);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            if (!promise.isDone()){
                promise.tryFailure(new RuntimeException());
            }else {
                fireClose();
            }
            super.channelInactive(ctx);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent){
                handleThrowable(new RuntimeException("connect idle timeout"));
            }else
                super.userEventTriggered(ctx, evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            handleThrowable(cause);
        }


    }


}
