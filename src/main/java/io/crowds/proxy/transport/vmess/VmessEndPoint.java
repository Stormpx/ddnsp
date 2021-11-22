package io.crowds.proxy.transport.vmess;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.vmess.stream.StreamCreator;
import io.crowds.proxy.transport.vmess.stream.TcpStreamCreator;
import io.crowds.proxy.transport.vmess.stream.WebSocketStreamCreator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
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

    private EventLoop eventLoop;
    private NetLocation netLocation;
    private VmessOption vmessOption;
    private ChannelCreator channelCreator;

    private Channel channel;
    private Promise<EndPoint> promise;
    private Promise<Void> closePromise;




    public VmessEndPoint(EventLoop eventLoop,NetLocation netLocation, VmessOption vmessOption, ChannelCreator channelCreator) {
        this.eventLoop=eventLoop;
        this.netLocation = netLocation;
        this.vmessOption = vmessOption;
        this.channelCreator = channelCreator;
        this.promise = eventLoop.newPromise();
        this.closePromise=eventLoop.newPromise();
    }


    private StreamCreator getCreator(){
        if ("ws".equalsIgnoreCase(vmessOption.getNetWork())&&vmessOption.getWs()!=null){
            return new WebSocketStreamCreator(eventLoop,vmessOption,channelCreator);
        }
        return new TcpStreamCreator(eventLoop,vmessOption,channelCreator);
    }

    public Future<EndPoint> init() throws Exception {
        var cf=getCreator().create();
        cf.addListener(future -> {
            if (!future.isSuccess()){
                promise.tryFailure(future.cause());
                return;
            }
            this.channel=cf.channel();
            this.channel.pipeline()
                    .addLast(new VmessMessageCodec())
                    .addLast(new VmessChannelHandler());

            handshake(this.channel);
        });
        return promise;
    }

    public Promise<EndPoint> getPromise() {
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
            channel.writeAndFlush(new VmessClose());
            channel.close();
        }
    }

    @Override
    public Future<Void> closeFuture() {
        return this.closePromise;
    }

    void tryActive(){
        if (this.promise.isSuccess())
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

    private void handshake(Channel channel){
        NetAddr dest = netLocation.getDest();

        User user = vmessOption.getUser();

        VmessRequest request = new VmessRequest( Set.of(Option.CHUNK_STREAM,Option.CHUNK_MASKING), netLocation.getTp(), dest);
        request.setUser(user.randomUser());
        request.setSecurity(vmessOption.getSecurity());

        channel.write(request)
                .addListener(future -> {
                    if (!future.isSuccess()) {
                        handleThrowable(future.cause());
                        return;
                    }
                });
        handleResponse();

    }

    public class VmessChannelHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            fireWriteable(ctx.channel().isWritable());
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
