package io.crowds.proxy.services.http;

import io.crowds.proxy.Axis;
import io.crowds.util.Inet;
import io.crowds.util.Strs;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Http1ServerHandler extends ChannelInitializer<SocketChannel> {

    private static final Logger logger = LoggerFactory.getLogger(Http1ServerHandler.class);
    private final HttpOption httpOption;
    private final Axis axis;

    public Http1ServerHandler(HttpOption httpOption, Axis axis) {
        this.httpOption = httpOption;
        this.axis = axis;
    }

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

    private class ProxyHandler extends ChannelInboundHandlerAdapter {
        private final RequestEncoder encoder;

        private Intermediary intermediary;
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
            try {
                String[] strs = str.split(":");
                var host=strs[0];
                var port= Integer.parseInt(strs.length<2?"80":strs[1]);
                return Inet.createSocketAddress(host, port);
            } catch (NumberFormatException e) {
                //ignore
                return null;
            }
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

        private void sendResponse(ChannelHandlerContext ctx,HttpRequest req,HttpResponseStatus responseStatus){
            ctx.writeAndFlush(new DefaultHttpResponse(req.protocolVersion(), responseStatus))
                    .addListener(ChannelFutureListener.CLOSE);
        }

        private void sendResponse(ChannelHandlerContext ctx,HttpRequest req,Throwable throwable){
            if (throwable instanceof ConnectException){
                sendResponse(ctx,req,HttpResponseStatus.BAD_GATEWAY);
            }else{
                sendResponse(ctx,req,HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }

        }

        private void determineIntermediary(ChannelHandlerContext ctx,HttpRequest req){
            if (req.method()== HttpMethod.CONNECT){
                this.intermediary = Intermediary.TUNNEL;
                InetSocketAddress address = getTarget(req.uri());
                if (address==null){
                    sendResponse(ctx,req,HttpResponseStatus.BAD_REQUEST);
                    return;
                }
                axis.handleTcp(ctx.channel(), ctx.channel().remoteAddress(), address)
                    .addListener(f->{
                        if (!f.isSuccess()) {
                            sendResponse(ctx, req,f.cause());
                            return;
                        }
                        ctx.writeAndFlush(new DefaultHttpResponse(req.protocolVersion(),new HttpResponseStatus(200,"Connection Established")));
                        releaseChannel(ctx);
                    });
            }else{
                InetSocketAddress address =null;
                if (req.uri().startsWith("/")){
                    String host = req.headers().get("host");
                    if (Strs.isBlank(host)||Objects.equals(httpOption.getHost(),host)) {
                        sendResponse(ctx,req,HttpResponseStatus.BAD_REQUEST);
                        return;
                    }

                    address=getTarget(URI.create(host));
                }else {
                    URI uri = URI.create(req.uri());
                    if (Strs.isBlank(uri.getHost())||Objects.equals(httpOption.getHost(),uri.getHost())) {
                        sendResponse(ctx,req,HttpResponseStatus.BAD_REQUEST);
                        return;
                    }
                    address = getTarget(uri);
                    req.headers().remove("proxy-connection").set(
                            HttpHeaderNames.HOST, uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ""));

                    if (req.method()==HttpMethod.OPTIONS&&Strs.isBlank(uri.getRawPath()) && Strs.isBlank(uri.getRawQuery())) {
                        req.setUri("*");
                    }else{
                        req.setUri(uri.getRawPath() + (Strs.isBlank(uri.getRawQuery()) ? "" : "?" + uri.getRawQuery()));
                    }
                }
                this.intermediary = Intermediary.PROXY;
                this.pendingReq=req;

                this.future=axis.handleTcp(ctx.channel(), ctx.channel().remoteAddress(), address)
                                .addListener(f->{
                                    if (!f.isSuccess()){
                                        sendResponse(ctx, req,f.cause());
                                        return;
                                    }
                                    ctx.pipeline().remove(HttpResponseEncoder.class);
                                    ByteBuf byteBuf = encoder.encodeRequest(ctx, pendingReq);
                                    ctx.fireChannelRead(byteBuf);
                                    releaseChannel(ctx);
                                });
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (intermediary==null){
                if (msg instanceof HttpRequest req){
                    determineIntermediary(ctx,req);
                    return;
                }
                ReferenceCountUtil.safeRelease(msg);
                ctx.close();
            }else if (intermediary==Intermediary.TUNNEL){
                if (!(msg instanceof LastHttpContent content) || content != LastHttpContent.EMPTY_LAST_CONTENT) {
                    ReferenceCountUtil.safeRelease(msg);
                    ctx.close();
                }
            }else{
                if (msg instanceof HttpContent payload) {
                    tryFireRead(ctx,payload.content());
                }else {
                    tryFireRead(ctx,msg);
                }
            }

        }


        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error("",cause);
            ctx.close();
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
