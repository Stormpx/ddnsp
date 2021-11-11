package io.crowds.proxy.transport.vmess.stream;
import io.crowds.proxy.transport.vmess.VmessClose;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

public class WebsocketMaskHandler extends ChannelDuplexHandler {

    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;

    public WebsocketMaskHandler(WebSocketClientHandshaker handshaker) {
        this.handshaker = handshaker;
    }

    public ChannelPromise handshakePromise() {
        return handshakeFuture;
    }

    public WebsocketMaskHandler handshakeFuture(ChannelPromise handshakeFuture) {
        this.handshakeFuture = handshakeFuture;
        return this;
    }

    public ChannelFuture handshake(){
        Channel channel = handshakeFuture.channel();
        handshaker.handshake(channel);
        return handshakeFuture;
    }


//    @Override
//    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
//        handshakeFuture = ctx.newPromise();
//    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof VmessClose){
            ctx.write(new CloseWebSocketFrame(WebSocketCloseStatus.NORMAL_CLOSURE),promise);
            return;
        }
        if (!(msg instanceof ByteBuf)) {
            super.write(ctx, msg, promise);
            return;
        }

        ctx.write(new BinaryWebSocketFrame((ByteBuf) msg),promise);

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            try {
                handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                handshakeFuture.trySuccess();
            } catch (WebSocketHandshakeException e) {
                handshakeFuture.tryFailure(e);
            }
            return;
        }

        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException(
                    "Unexpected FullHttpResponse (getStatus=" + response.status() +
                            ", content=" + response.content().toString(CharsetUtil.UTF_8) + ')');
        }

        WebSocketFrame frame = (WebSocketFrame) msg;

        if (frame instanceof PingWebSocketFrame) {
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content()));
        } else if (frame instanceof CloseWebSocketFrame) {
            ch.close();
        }else if (frame instanceof PongWebSocketFrame){
            ReferenceCountUtil.safeRelease(frame);
        }else{
            ctx.fireChannelRead(frame.content());
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (!handshakeFuture.isDone()) {
            handshakeFuture.tryFailure(cause);
        }else {
            super.exceptionCaught(ctx, cause);
        }
    }
}
