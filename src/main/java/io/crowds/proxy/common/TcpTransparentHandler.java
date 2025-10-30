package io.crowds.proxy.common;

import io.crowds.proxy.Axis;
import io.crowds.proxy.dns.FakeContext;
import io.crowds.proxy.dns.FakeDns;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class TcpTransparentHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(TcpTransparentHandler.class);

    private final Axis axis;

    public TcpTransparentHandler(Axis axis) {
        this.axis = axis;
    }

    private boolean accept(InetSocketAddress destAddr){
        InetAddress dest = destAddr.getAddress();
        FakeDns fakeDns = axis.getFakeDns();
        if (fakeDns !=null&&fakeDns.isFakeIp(dest)){
            FakeContext fake = fakeDns.getFake(dest);
            return fake != null;
        }
        return true;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        SocketAddress remoteAddress = channel.localAddress();
        if (logger.isDebugEnabled())
            logger.debug("tcp remote addr:{}",remoteAddress);
        if (!accept((InetSocketAddress) remoteAddress)){
            ctx.close();
            return;
        }
        axis.handleTcp(channel, channel.remoteAddress(), remoteAddress);
        super.channelActive(ctx);
    }


}
