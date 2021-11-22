package io.crowds.proxy.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultProgressivePromise;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;

import java.util.function.Consumer;


public abstract class EndPoint {

    private Consumer<Boolean> writabilityHandler;

    private Consumer<ByteBuf> bufferHandler;

    public abstract void write(ByteBuf buf);

    public void setAutoRead(boolean autoRead){
        channel().config().setAutoRead(autoRead);
    }

    public abstract Channel channel();

    public abstract void close();

    public abstract Future<Void> closeFuture();

    protected void fireBuf(ByteBuf buf){
        if(this.bufferHandler==null){
            ReferenceCountUtil.safeRelease(buf);
            return ;
        }
        this.bufferHandler.accept(buf);
    }

    protected void fireWriteable(boolean writeable){
        if (this.writabilityHandler==null){
            return;
        }
        this.writabilityHandler.accept(writeable);
    }

    public void bufferHandler(Consumer<ByteBuf> bufferHandler){
        this.bufferHandler=bufferHandler;
    }

    public void writabilityHandler(Consumer<Boolean> writabilityHandler) {
        this.writabilityHandler = writabilityHandler;
    }
}
