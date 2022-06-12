package io.crowds.proxy.transport;

import io.netty.channel.Channel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;


public abstract class EndPoint  {
    private final static Logger logger= LoggerFactory.getLogger(EndPoint.class);
    private final static Consumer<Throwable> DEFAULT_EXCEPTION_HANDLER=t->{
          logger.error("",t);
    };

    private Consumer<Boolean> writabilityHandler;

    private Consumer<Object> messageHandler;

    private Consumer<Throwable> exceptionHandler=DEFAULT_EXCEPTION_HANDLER;

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

    protected void fireException(Throwable cause){
        if (this.exceptionHandler!=null)
            this.exceptionHandler.accept(cause);
    }

    public void bufferHandler(Consumer<Object> messageHandler){
        this.messageHandler =messageHandler;
    }

    public void writabilityHandler(Consumer<Boolean> writabilityHandler) {
        this.writabilityHandler = writabilityHandler;
    }

    public void exceptionHandler(Consumer<Throwable> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }
}
