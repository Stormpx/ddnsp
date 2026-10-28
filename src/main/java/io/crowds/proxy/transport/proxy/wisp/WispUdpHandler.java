package io.crowds.proxy.transport.proxy.wisp;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * bridges the accepted (child) side of a udp stream with the wisp stream.
 */
public class WispUdpHandler extends ChannelInboundHandlerAdapter {
    private final static Logger logger = LoggerFactory.getLogger(WispUdpHandler.class);

    private final WispUdpStream stream;

    public WispUdpHandler(WispUdpStream stream) {
        this.stream = stream;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        stream.attach(ctx.channel());
        super.handlerAdded(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        stream.open();
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof DatagramPacket packet) {
            try {
                stream.write(packet);
            } finally {
                ReferenceCountUtil.safeRelease(packet);
            }
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
            logger.debug("wisp udp stream {}: local channel error", stream.id(), cause);
        }
        ctx.close();
    }
}
