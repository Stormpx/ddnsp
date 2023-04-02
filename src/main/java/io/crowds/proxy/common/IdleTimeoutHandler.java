package io.crowds.proxy.common;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;

import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class IdleTimeoutHandler extends IdleStateHandler {
    public final static AttributeKey<Void> IGNORE_IDLE_FLAG =AttributeKey.valueOf("ignore_idle_flag");
    private BiConsumer<Channel,IdleStateEvent> idleEventHandler;
    private boolean closed;

    public IdleTimeoutHandler( int allIdleTimeSeconds,BiConsumer<Channel,IdleStateEvent> idleEventHandler) {
        super(0, 0, allIdleTimeSeconds);
        this.idleEventHandler=idleEventHandler;
    }

    public IdleTimeoutHandler(long allIdleTime, TimeUnit unit,BiConsumer<Channel,IdleStateEvent> idleEventHandler) {
        super(0, 0, allIdleTime, unit);
        this.idleEventHandler=idleEventHandler;
    }


    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        closed=true;
        super.close(ctx, promise);
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
        if (ctx.channel().hasAttr(IGNORE_IDLE_FLAG)){
            return;
        }
        if (this.idleEventHandler!=null)
            this.idleEventHandler.accept(ctx.channel(),evt);
        else {
            if (!closed){
                ctx.close();
                closed=true;
            }
        }
    }
}
