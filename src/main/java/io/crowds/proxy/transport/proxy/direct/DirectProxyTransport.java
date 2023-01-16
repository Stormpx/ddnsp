package io.crowds.proxy.transport.proxy.direct;

import io.crowds.proxy.*;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.proxy.FullConeProxyTransport;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.concurrent.*;

import java.net.InetSocketAddress;
import java.util.Optional;

public class DirectProxyTransport extends FullConeProxyTransport {

    private ProtocolOption protocolOption;

    public DirectProxyTransport(ChannelCreator channelCreator) {
        this(channelCreator,new ProtocolOption());
    }


    public DirectProxyTransport(ChannelCreator channelCreator, ProtocolOption protocolOption) {
        super(channelCreator,protocolOption);
        this.protocolOption = protocolOption;
    }

    @Override
    public String getTag() {
        return Optional.ofNullable(protocolOption)
                .map(ProtocolOption::getName)
                .orElse("direct");
    }

    @Override
    protected Future<Channel> proxy(Channel channel, NetLocation netLocation) {
        channel.pipeline().addLast(new DirectOutboundHandler());
        return channel.eventLoop().newSucceededFuture(channel);
    }

    private static class DirectOutboundHandler extends ChannelOutboundHandlerAdapter{

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof DatagramPacket packet){
                InetSocketAddress recipient = packet.recipient();
                if (recipient.isUnresolved()){
                    msg = new DatagramPacket(packet.content(),new InetSocketAddress(recipient.getHostString(),recipient.getPort()),packet.sender());
                }
            }
            super.write(ctx, msg, promise);
        }
    }

}
