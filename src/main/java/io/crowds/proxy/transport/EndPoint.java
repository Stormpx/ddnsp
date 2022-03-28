package io.crowds.proxy.transport;

import io.netty.channel.Channel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;

import java.util.function.Consumer;


public abstract class EndPoint  {

    private Consumer<Boolean> writabilityHandler;

    private Consumer<Object> messageHandler;

    public abstract void write(Object buf);

    public void setAutoRead(boolean autoRead){
        channel().config().setAutoRead(autoRead);
    }

    public abstract Channel channel();

    public abstract void close();

    public abstract Future<Void> closeFuture();

    protected void fireBuf(Object buf){
        if(this.messageHandler ==null){
            ReferenceCountUtil.safeRelease(buf);
            return ;
        }
        this.messageHandler.accept(buf);
    }

    protected void fireWriteable(boolean writeable){
        if (this.writabilityHandler==null){
            return;
        }
        this.writabilityHandler.accept(writeable);
    }

    public void bufferHandler(Consumer<Object> messageHandler){
        this.messageHandler =messageHandler;
    }

    public void writabilityHandler(Consumer<Boolean> writabilityHandler) {
        this.writabilityHandler = writabilityHandler;
    }
}
