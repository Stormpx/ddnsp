package io.crowds.proxy.transport;

import io.crowds.util.Exceptions;
import io.netty.channel.Channel;
import io.netty.channel.socket.DuplexChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;


public abstract class EndPoint  {
    protected final static Logger logger= LoggerFactory.getLogger(EndPoint.class);
    private final static Consumer<Throwable> DEFAULT_EXCEPTION_HANDLER=t->{
        if (!Exceptions.isExpected(t)){
            logger.error("",t);
        }
    };

    public enum Shutdown{
        INPUT,OUTPUT;

        public Shutdown reverse(){
            return this==INPUT?OUTPUT:INPUT;
        }
    }

    private Consumer<Boolean> writabilityHandler;

    private Consumer<Object> messageHandler;

    private Runnable readCompleteHandler;

    private Consumer<Throwable> exceptionHandler=DEFAULT_EXCEPTION_HANDLER;

    private Consumer<Shutdown> shutdownHandler;

    public abstract void write(Object buf);

    public void flush(){
        channel().flush();
    }

    public void setAutoRead(boolean autoRead){
        channel().config().setAutoRead(autoRead);
    }

    public abstract Channel channel();

    public void shutdown(Shutdown shutdown) {
        close();
    }

    public abstract void close();

    public abstract Future<Void> closeFuture();

    protected void fireBuf(Object buf){
        if(this.messageHandler ==null){
            ReferenceCountUtil.safeRelease(buf);
            return ;
        }
        this.messageHandler.accept(buf);
    }
    protected void fireReadComplete(){
        Runnable readCompleteHandler = this.readCompleteHandler;
        if (readCompleteHandler!=null){
            readCompleteHandler.run();
        }
    }

    protected void fireWriteable(boolean writeable){
        if (this.writabilityHandler==null){
            return;
        }
        this.writabilityHandler.accept(writeable);
    }

    protected void fireShutdown(Shutdown shutdown){
        if (this.shutdownHandler==null){
            return;
        }
        this.shutdownHandler.accept(shutdown);
    }

    protected void fireException(Throwable cause){
        if (this.exceptionHandler!=null)
            this.exceptionHandler.accept(cause);
    }

    public void bufferHandler(Consumer<Object> messageHandler){
        this.messageHandler =messageHandler;
    }

    public void readCompleteHandler(Runnable readCompleteHandler) {
        this.readCompleteHandler = readCompleteHandler;
    }

    public void writabilityHandler(Consumer<Boolean> writabilityHandler) {
        this.writabilityHandler = writabilityHandler;
    }

    public EndPoint shutdownHandler(Consumer<Shutdown> shutdownHandler) {
        this.shutdownHandler = shutdownHandler;
        return this;
    }

    public void exceptionHandler(Consumer<Throwable> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }


}
