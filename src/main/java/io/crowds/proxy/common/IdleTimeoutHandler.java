package io.crowds.proxy.common;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class IdleTimeoutHandler extends IdleStateHandler {

    private Consumer<IdleStateEvent> idleEventHandler;
    private boolean closed;

    public IdleTimeoutHandler( int allIdleTimeSeconds,Consumer<IdleStateEvent> idleEventHandler) {
        super(0, 0, allIdleTimeSeconds);
        this.idleEventHandler=idleEventHandler;
    }

    public IdleTimeoutHandler(long allIdleTime, TimeUnit unit,Consumer<IdleStateEvent> idleEventHandler) {
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
        if (this.idleEventHandler!=null)
            this.idleEventHandler.accept(evt);
        else {
            if (!closed){
                ctx.close();
                closed=true;
            }
        }
    }
}
