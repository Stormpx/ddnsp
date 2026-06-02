package io.crowds.proxy.services.http;

import io.crowds.compoments.capsule.Capsule;
import io.crowds.compoments.capsule.CapsuleDecoder;
import io.crowds.compoments.capsule.CapsuleEncoder;
import io.crowds.proxy.Axis;
import io.crowds.proxy.ProxyContext;
import io.crowds.util.Inet;
import io.crowds.util.Strs;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
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

public class Http1ServerHandler extends HttpServerHandler {

    private static final Logger logger = LoggerFactory.getLogger(Http1ServerHandler.class);

    public Http1ServerHandler(HttpOption httpOption, Axis axis) {
        super(httpOption, axis);
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
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
            if (!ctx.channel().isActive()){
                return;
            }
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

        private void handleUdp(ChannelHandlerContext ctx,HttpRequest req,URI uri){
            if (req.method()!=HttpMethod.GET){
                sendResponse(ctx,req,HttpResponseStatus.BAD_REQUEST);
                return;
            }
            String host = req.headers().get(HttpHeaderNames.HOST);
            String connection = req.headers().get(HttpHeaderNames.CONNECTION);
            String upgrade = req.headers().get(HttpHeaderNames.UPGRADE);
            String capsuleProtocol = req.headers().get("capsule-protocol");
            if (Strs.isBlank(host)
                    || !"upgrade".equalsIgnoreCase(connection)
                    || !"connect-udp".equalsIgnoreCase(upgrade)
                    || !"?1".equals(capsuleProtocol)) {
                sendResponse(ctx, req, HttpResponseStatus.BAD_REQUEST);
                return;
            }
            var address = extractUdpTargetFromUri(uri);
            if (address==null){
                sendResponse(ctx,req,HttpResponseStatus.BAD_REQUEST);
                return;
            }
            ctx.pipeline().remove(HttpRequestDecoder.class);
            ctx.pipeline().remove(HttpServerExpectContinueHandler.class);
            ctx.pipeline().addLast(new CapsuleDecoder(65527),new CapsuleEncoder(),new DatagramRelayer(address));
            DefaultHttpResponse response = new DefaultHttpResponse(req.protocolVersion(), HttpResponseStatus.SWITCHING_PROTOCOLS);
            response.headers()
                    .add(HttpHeaderNames.CONNECTION,HttpHeaderValues.UPGRADE)
                    .add(HttpHeaderNames.UPGRADE,"connect-udp")
                    .add("capsule-protocol","?1");
            ctx.writeAndFlush(response)
               .addListener(f->{
                   if (!f.isSuccess()){
                       ctx.close();
                       return;
                   }
                   ctx.pipeline().remove(HttpResponseEncoder.class);
                   ctx.pipeline().remove(this);
               });

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
                        ctx.channel().attr(ProxyContext.SEND_ZC_SUPPORTED);
                    });
            }else{
                URI uri = URI.create(req.uri());
                InetSocketAddress targetAddress;
                if (isUdpRequest(uri)){
                    this.intermediary = Intermediary.TUNNEL;
                    handleUdp(ctx,req,uri);
                    return;
                }
                if (uri.getHost()==null){
                    String host = req.headers().get("host");
                    if (Strs.isBlank(host)||Objects.equals(httpOption.getHost(),host)) {
                        sendResponse(ctx,req,HttpResponseStatus.BAD_REQUEST);
                        return;
                    }

                    targetAddress=getTarget(URI.create(host));
                }else {
                    if (Strs.isBlank(uri.getHost())||Objects.equals(httpOption.getHost(),uri.getHost())) {
                        sendResponse(ctx,req,HttpResponseStatus.BAD_REQUEST);
                        return;
                    }
                    targetAddress = getTarget(uri);
                    req.headers().remove("proxy-connection").set(
                            HttpHeaderNames.HOST, uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ""));

                    if (req.method()==HttpMethod.OPTIONS&&Strs.isBlank(uri.getRawPath()) && Strs.isBlank(uri.getRawQuery())) {
                        req.setUri("*");
                    }else{
                        req.setUri(uri.getRawPath() + (Strs.isBlank(uri.getRawQuery()) ? "" : "?" + uri.getRawQuery()));
                    }
                }
                this.intermediary = Intermediary.PROXY;

                final HttpRequest pendingReq=req;
                this.future=axis.handleTcp(ctx.channel(), ctx.channel().remoteAddress(), targetAddress)
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
                if (!(msg instanceof LastHttpContent content && content == LastHttpContent.EMPTY_LAST_CONTENT)) {
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

    private class DatagramRelayer extends ChannelDuplexHandler{

        private final InetSocketAddress targetAddress;

        private DatagramRelayer(InetSocketAddress targetAddress) {
            Objects.requireNonNull(targetAddress);
            this.targetAddress = targetAddress;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!(msg instanceof Capsule capsule)) {
                ReferenceCountUtil.safeRelease(msg);
                if (!(msg instanceof LastHttpContent)) {
                    ctx.close();
                }
                return;
            }
            if (capsule.type() != Capsule.TYPE_DATAGRAM){
                ReferenceCountUtil.safeRelease(msg);
                return;
            }
            axis.handleUdp0(ctx.channel(),
                    new DatagramPacket(capsule.content(), targetAddress, (InetSocketAddress) ctx.channel().remoteAddress()),
                    ReferenceCountUtil::safeRelease);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (!(msg instanceof DatagramPacket packet)){
                ReferenceCountUtil.safeRelease(msg);
                return;
            }
            ctx.write(Capsule.datagram(packet.content()),promise);
        }
    }

}
