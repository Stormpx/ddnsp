package io.crowds.proxy.transport.proxy.wisp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * bridges the accepted (child) side of a tcp stream with the wisp stream.
 * <p>
 * inbound messages come from the local client, the payload of the destination server is
 * injected into the pipeline of the other side (the channel of the caller).
 */
public class WispTcpHandler extends ChannelInboundHandlerAdapter {
    private final static Logger logger = LoggerFactory.getLogger(WispTcpHandler.class);

    private final WispTcpStream stream;

    public WispTcpHandler(WispTcpStream stream) {
        this.stream = stream;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        stream.attach(ctx.channel(), ((WispLocalChannel) ctx.channel()).peer());
        super.handlerAdded(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        stream.open();
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf buf) {
            stream.write(buf);
            return;
        }
        ReferenceCountUtil.safeRelease(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        stream.onLocalClosed();
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("wisp stream {}: local channel error", stream.id(), cause);
        }
        ctx.close();
    }
}
