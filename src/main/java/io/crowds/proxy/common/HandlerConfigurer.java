package io.crowds.proxy.common;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.Objects;

public class HandlerConfigurer extends ChannelInboundHandlerAdapter {


    private ChannelHandler[] channelHandlers;

    public HandlerConfigurer(ChannelHandler... channelHandlers) {
        Objects.requireNonNull(channelHandlers,"handles");
        this.channelHandlers = channelHandlers;
    }


    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        for (ChannelHandler channelHandler : channelHandlers) {
            ctx.channel().pipeline().addLast(channelHandler);
        }
        super.handlerAdded(ctx);
    }
}
