package io.crowds.proxy.services.http;

import io.crowds.Platform;
import io.crowds.proxy.Axis;
import io.crowds.util.Inet;
import io.crowds.util.Strs;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollMode;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.unix.UnixChannelOption;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HttpServer {
    private final static Logger logger= LoggerFactory.getLogger(HttpServer.class);

    private Axis axis;
    private HttpOption httpOption;

    public HttpServer( HttpOption httpOption,Axis axis) {
        Objects.requireNonNull(axis);
        Objects.requireNonNull(httpOption);
        this.axis = axis;
        this.httpOption = httpOption;
    }

    public Future<Void> start(){
        Promise<Void> promise=Promise.promise();
        InetSocketAddress socketAddress = new InetSocketAddress(httpOption.getHost(), httpOption.getPort());
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        ServerBootstrap bootstrap = serverBootstrap.group(axis.getAcceptor(), axis.getEventLoopGroup())
                .channel(Platform.getServerSocketChannelClass());
        if (Epoll.isAvailable()){
            bootstrap.option(UnixChannelOption.SO_REUSEPORT,true)
                     .childOption(EpollChannelOption.EPOLL_MODE, EpollMode.LEVEL_TRIGGERED);
        }
        bootstrap
                .option(ChannelOption.SO_REUSEADDR,true)
                .childOption(ChannelOption.SO_REUSEADDR,true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        HttpRequestDecoder decoder = new HttpRequestDecoder();
                        decoder.setSingleDecode(true);
                        ch.pipeline().addLast(decoder)
                                .addLast(new HttpResponseEncoder())
                                .addLast(new HttpServerExpectContinueHandler())
                                .addLast(new ProxyHandler())
                        ;

                    }
                })
                .bind(socketAddress)
                .addListener(future -> {
                    if (future.isSuccess()) {
                        promise.complete();
                        logger.info("start http proxy server {}", socketAddress);
                    }else {
                        future.cause().printStackTrace();
                        promise.tryFail(future.cause());
                        logger.error("start http proxy server failed cause:{}", future.cause().getMessage());
                    }
                });
        return promise.future();
    }



    private class ProxyHandler extends ChannelInboundHandlerAdapter{
        private RequestEncoder encoder;

        private HttpRequest pendingReq;

        private io.netty.util.concurrent.Promise<Void> future;

        public ProxyHandler() {
            this.encoder=new RequestEncoder();
        }

        private void releaseChannel(ChannelHandlerContext ctx){
            ChannelPipeline pipeline = ctx.channel().pipeline();
            while (pipeline.removeFirst()!=this){

            }
        }

        private InetSocketAddress getTarget(String str){
            String[] strs = str.split(":");
            var host=strs[0];
            var port= Integer.parseInt(strs.length<2?"80":strs[1]);

            InetSocketAddress address = Inet.createSocketAddress(host, port);
            return address;
        }

        private InetSocketAddress getTarget(URI uri){
            var host=uri.getHost();
            var port=uri.getPort();
            if (port<=0){
                port= uri.getScheme().startsWith("https") ? 443 : 80;
            }
            return Inet.createSocketAddress(host,port);
        }

        private void tryFireRead(ChannelHandlerContext ctx,Object msg){
            Objects.requireNonNull(future,"should not happen");
            if (future.isDone()){
                if (!future.isSuccess()){
                    ReferenceCountUtil.safeRelease(msg);
                    return;
                }
                ctx.fireChannelRead(msg);
            }else{
                future.addListener(f->{
                    if (!future.isSuccess()){
                        ReferenceCountUtil.safeRelease(msg);
                        return;
                    }
                    ctx.fireChannelRead(msg);
                });
            }

        }


        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof HttpRequest req){
//                System.out.println(req);
//                System.out.println();
                if (req.method()== HttpMethod.CONNECT){
                    InetSocketAddress address = getTarget(req.uri());

                    ctx.channel()
                            .writeAndFlush(new DefaultHttpResponse(req.protocolVersion(),new HttpResponseStatus(200,"Connection Established")))
                            .addListener(f->{
                                if (f.isSuccess()) {
                                    axis.handleTcp(ctx.channel(), ctx.channel().remoteAddress(), address);
                                    releaseChannel(ctx);
                                }
                            });

                }else{
                    InetSocketAddress address =null;
                    if (req.uri().startsWith("/")){
                        String host = req.headers().get("host");
                        if (Strs.isBlank(host)||Objects.equals(httpOption.getHost(),host)) {
                            ctx.channel().writeAndFlush(new DefaultHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST))
                                    .addListener(ChannelFutureListener.CLOSE);
                            return;
                        }

                        address=getTarget(URI.create(host));
                    }else {
                        URI uri = URI.create(req.uri());
                        if (Strs.isBlank(uri.getHost())||Objects.equals(httpOption.getHost(),uri.getHost())) {
                            ctx.channel().writeAndFlush(new DefaultHttpResponse(req.protocolVersion(), HttpResponseStatus.BAD_REQUEST))
                                    .addListener(ChannelFutureListener.CLOSE);
                            return;
                        }
                        address = getTarget(uri);
                        req.headers().remove("proxy-connection").set(HttpHeaderNames.HOST, uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ""));

                        req.setUri(uri.getRawPath() + (Strs.isBlank(uri.getRawQuery()) ? "" : "?" + uri.getRawQuery()));
                    }
                    this.pendingReq=req;


                    ctx.pipeline().remove(HttpResponseEncoder.class);

                    this.future=axis.handleTcp(ctx.channel(), ctx.channel().remoteAddress(), address)
                            .addListener(f->{
                                if (!f.isSuccess()){
                                    return;
                                }
                                ByteBuf byteBuf = encoder.encodeRequest(ctx, pendingReq);
                                ctx.fireChannelRead(byteBuf);
                                releaseChannel(ctx);
                            });
                }
            }else {

                if (msg instanceof HttpContent payload) {
                    tryFireRead(ctx,payload.content());
                }else {
                    tryFireRead(ctx,msg);
                }
            }

        }


        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
        }
    }

    private class RequestEncoder extends HttpRequestEncoder{

        public ByteBuf encodeRequest(ChannelHandlerContext ctx,HttpRequest request) throws Exception {
            List<Object> result=new ArrayList<>(1);
            encode(ctx,request,result);
            if (!result.isEmpty()){
                return (ByteBuf) result.get(0);
            }
            return null;
        }

    }

}
