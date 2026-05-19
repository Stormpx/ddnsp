package io.crowds.proxy.services.http;

import io.crowds.compoments.capsule.Capsule;
import io.crowds.compoments.capsule.CapsuleDecoder;
import io.crowds.compoments.capsule.CapsuleEncoder;
import io.crowds.proxy.Axis;
import io.crowds.proxy.common.IdleTimeoutHandler;
import io.crowds.util.Inet;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.*;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Http2ServerHandler extends HttpServerHandler {

    private final static Logger logger = LoggerFactory.getLogger(Http2ServerHandler.class);
    private final static Consumer<Http2Headers> NOP = t->{};

    public Http2ServerHandler(HttpOption httpOption, Axis axis) {
        super(httpOption, axis);
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ch.pipeline()
          .addLast(new IdleTimeoutHandler(1, TimeUnit.HOURS,(c,evt)->c.close()))
          .addLast(Http2FrameCodecBuilder.forServer().autoAckPingFrame(true).autoAckSettingsFrame(true).build())
          .addLast(new ChannelInboundHandlerAdapter(){
              @Override
              public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                  if (msg instanceof Http2SettingsFrame || msg instanceof Http2SettingsAckFrame){
                      if (logger.isDebugEnabled()){
                          logger.debug("{}",msg);
                      }
                      return;
                  }
                  super.channelRead(ctx, msg);
              }
          })
          .addLast(new Http2MultiplexHandler(new ChannelInitializer<>() {
              @Override
              protected void initChannel(Channel ch) throws Exception {
                  ch.pipeline().addLast(new ProxyHandler());
              }
          }));
    }


    private class ProxyHandler extends ChannelDuplexHandler {
        private Intermediary intermediary;

        private InetSocketAddress getTarget(String str){
            String[] strs = str.split(":");
            var host=strs[0];
            var port= Integer.parseInt(strs.length<2?"80":strs[1]);

            return Inet.createSocketAddress(host, port);
        }

        private void sendResponseStatus(ChannelHandlerContext ctx, HttpResponseStatus status, boolean endStream, Consumer<Http2Headers> addHeaders){
            if (!ctx.channel().isActive()){
                return;
            }
            DefaultHttp2Headers headers = new DefaultHttp2Headers();
            headers.status(status.codeAsText());
            addHeaders.accept(headers);
            DefaultHttp2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(headers, endStream);
            ChannelFuture future = ctx.writeAndFlush(headersFrame);
            if (endStream){
                future.addListener(ChannelFutureListener.CLOSE);
            }
        }

        private void sendResponseStatus(ChannelHandlerContext ctx,HttpResponseStatus status,boolean endStream){
            sendResponseStatus(ctx,status,endStream,NOP);
        }


        private void sendReset(ChannelHandlerContext ctx,Http2Error error){
            ctx.fireExceptionCaught(new Http2Exception(error));
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof Http2HeadersFrame headersFrame) {
                Http2Headers headers = headersFrame.headers();
                if (intermediary!=null){
                    sendResponseStatus(ctx, HttpResponseStatus.BAD_REQUEST, true);
                }else {
                    if ("CONNECT".contentEquals(headers.method())) {
                        intermediary = Intermediary.TUNNEL;
                        if (headersFrame.isEndStream()) {
                            sendResponseStatus(ctx, HttpResponseStatus.BAD_REQUEST, true);
                            return;
                        }
                        URI uri = URI.create(headers.path().toString());
                        if (isUdpRequest(uri)){
                            var protocol = headers.get(Http2Headers.PseudoHeaderName.PROTOCOL.value());
                            var capsuleProtocol = headers.get("capsule-protocol");
                            if (!"connect-udp".contentEquals(protocol)||!"?1".contentEquals(capsuleProtocol)){
                                sendResponseStatus(ctx, HttpResponseStatus.BAD_REQUEST, true);
                                return;
                            }
                            var target = extractUdpTargetFromUri(uri);
                            ctx.pipeline().addLast(new CapsuleDecoder(65527),new CapsuleEncoder(),new DatagramRelayer(target));
                            sendResponseStatus(ctx,HttpResponseStatus.OK,false,h->{
                                h.add("capsule-protocol","?1");
                            });

                        }else{
                            var target = getTarget(headers.authority().toString());
                            axis.handleTcp(ctx.channel(), ctx.channel().remoteAddress(), target)
                                .addListener(future -> {
                                    if (!future.isSuccess()) {
                                        sendResponseStatus(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, true);
                                        return;
                                    }
                                    sendResponseStatus(ctx, HttpResponseStatus.OK, false);
                                });
                        }

                    } else {
                        sendResponseStatus(ctx, HttpResponseStatus.BAD_REQUEST, true);
                    }
                }
            } else if (msg instanceof Http2DataFrame dataFrame) {
                if (intermediary == Intermediary.TUNNEL) {
                    boolean endStream = dataFrame.isEndStream();
                    ctx.fireChannelRead(dataFrame.content());
                    if (endStream){
                        ctx.channel().close();
                    }
                } else {
                    sendReset(ctx, Http2Error.INTERNAL_ERROR);
                    ReferenceCountUtil.safeRelease(msg);
                }
            } else {
                ReferenceCountUtil.safeRelease(msg);
            }
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (intermediary==Intermediary.TUNNEL){
                if (msg instanceof ByteBuf buf){
                    DefaultHttp2DataFrame dataFrame = new DefaultHttp2DataFrame(buf);
                    ctx.write(dataFrame,promise);
                    return;
                }
            }
            super.write(ctx,msg,promise);
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            if (intermediary==Intermediary.TUNNEL) {
                //close from target side, sends a data frame with the END_STREAM flag
                ctx.writeAndFlush(new DefaultHttp2DataFrame(true), promise)
                   .addListener(ChannelFutureListener.CLOSE);
            }else{
                super.close(ctx,promise);
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof Http2ResetFrame resetFrame){
                logger.info("stream:{} receive reset",resetFrame.stream().id());
            }else if (evt instanceof Http2GoAwayFrame goAwayFrame){
                logger.info("stream receive goaway");
            }
            super.userEventTriggered(ctx, evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            logger.error("",cause);
            ctx.close();
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
                ctx.close();
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
