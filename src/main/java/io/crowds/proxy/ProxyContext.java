package io.crowds.proxy;

import io.crowds.proxy.dns.FakeContext;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.TcpEndPoint;
import io.crowds.util.Exceptions;
import io.netty.channel.EventLoop;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ProxyContext {
    private final static Logger logger= LoggerFactory.getLogger(ProxyContext.class);
    public final static AttributeKey<Void> SEND_ZC_SUPPORTED =AttributeKey.valueOf("send_zc_supported");

    private final static VarHandle CLOSE;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            CLOSE = l.findVarHandle(ProxyContext.class, "close", boolean.class);
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }
    private Ticker ticker = Ticker.systemTicker();
    private final EventLoop eventLoop;
    private EndPoint src;
    private EndPoint dst;
    private NetLocation netLocation;
    private List<NetLocation> prevLocations;

    private String tag;
    private FakeContext fakeContext;

    private Consumer<DatagramPacket> fallbackPacketHandler;

    private boolean close;
    private Consumer<Void> closeHandler;

    private final HalfClosureTimer halfClosureTimer = new HalfClosureTimer();

    public ProxyContext(EventLoop eventLoop, NetLocation netLocation) {
        this.eventLoop = eventLoop;
        this.netLocation = Objects.requireNonNull(netLocation);
    }

    private class HalfClosureTimer implements Runnable{
        private static final long timeoutNanos = TimeUnit.MINUTES.toNanos(30);
        private volatile Future<?> timeout;
        private volatile long lastFlushTime;

        private boolean isScheduled(){
            return timeout!=null;
        }

        private void recordFlushTime(){
            if (isScheduled()) {
                lastFlushTime = ticker.nanoTime();
            }
        }
        private void cancel(){
            if (timeout!=null){
                timeout.cancel(false);
                timeout = null;
            }
        }
        private void schedule(long delay){
            timeout = eventLoop.schedule(this,delay,TimeUnit.NANOSECONDS);
        }

        private void schedule(){
            schedule(timeoutNanos);
        }

        @Override
        public void run() {
            if (close){
                return;
            }
            long nextDelay = timeoutNanos - (ticker.nanoTime() - lastFlushTime);

            if (nextDelay <= 0){
                src.close();
                dst.close();
                return;
            }

            schedule(nextDelay);

        }
    }

    private boolean isSpliceAvailable(EndPoint src,EndPoint dst){
        return false;
//        return src instanceof TcpEndPoint && dst instanceof TcpEndPoint
//                && src.channel() instanceof EpollSocketChannel srcSocket
//                && dst.channel() instanceof EpollSocketChannel dstSocket
//                && srcSocket.hasAttr(SEND_ZC_SUPPORTED)
//                && dstSocket.hasAttr(SEND_ZC_SUPPORTED)
//                && srcSocket.eventLoop()==dstSocket.eventLoop();
    }

    private void flushEndPoint(EndPoint target){
        halfClosureTimer.recordFlushTime();
        target.flush();
    }

    private void shutdownEndpoint(EndPoint peer, EndPoint.Shutdown shutdown, EndPoint target){
        if (!halfClosureTimer.isScheduled()) {
            halfClosureTimer.schedule();
        }
        target.shutdown(shutdown.reverse());
    }

    public void bridging(EndPoint src,EndPoint dst){
        dst.bufferHandler(src::write);
        dst.readCompleteHandler(()->flushEndPoint(src));
        src.bufferHandler(dst::write);
        src.readCompleteHandler(()->flushEndPoint(dst));
        src.writabilityHandler(dst::setAutoRead);
        dst.writabilityHandler(src::setAutoRead);
        src.shutdownHandler(shutdown-> shutdownEndpoint(src,shutdown,dst));
        dst.shutdownHandler(shutdown-> shutdownEndpoint(dst,shutdown,src));
        src.closeFuture().addListener(closeFuture->{
            fireClose();
            dst.close();
        });
        dst.closeFuture().addListener(closeFuture->{
            fireClose();
            src.close();
        });
        this.src=src;
        this.dst=dst;

    }

    public void setAutoRead(){
        if (isSpliceAvailable(src,dst)){
            var srcSocket = (EpollSocketChannel)src.channel();
            var dstSocket = (EpollSocketChannel)dst.channel();
            var srcSpliceFuture = srcSocket.spliceTo(dstSocket,Integer.MAX_VALUE);
            var dstSpliceFuture = dstSocket.spliceTo(srcSocket,Integer.MAX_VALUE);
            srcSpliceFuture.addListener(f->{
                if (!f.isSuccess()&&dstSocket.isActive()&&!Exceptions.isExpected(f.cause())){
                    logger.error("{}",f.cause().getMessage());
                }
                fireClose();
                src.close();
            });
            dstSpliceFuture.addListener(f->{
                if (!f.isSuccess()&&srcSocket.isActive()&&!Exceptions.isExpected(f.cause())){
                    logger.error("{}",f.cause().getMessage());
                }
                fireClose();
                dst.close();
            });
        }
        src.setAutoRead(true);
        dst.setAutoRead(true);
        eventLoop.submit(()->{
            boolean shutdown = false;
            if (src.getShutdown()!=null){
                shutdown = true;
                dst.shutdown(src.getShutdown().reverse());
            }
            if (dst.getShutdown()!=null){
                shutdown = true;
                src.shutdown(dst.getShutdown().reverse());
            }
            if (shutdown){
                halfClosureTimer.schedule();
            }
        });

    }

    private void fireClose(){
        halfClosureTimer.cancel();
        Consumer<Void> closeHandler = this.closeHandler;
        if (CLOSE.compareAndSet(this,false,true)){
            if (closeHandler!=null)
                closeHandler.accept(null);
        }
    }

    public ProxyContext withFakeContext(FakeContext fakeContext) {
        this.fakeContext = fakeContext;
        return this;
    }

    public ProxyContext withTag(String tag) {
        this.tag = tag;
        return this;
    }

    public ProxyContext withNetLocation(NetLocation netLocation){
        Objects.requireNonNull(netLocation);
        NetLocation prevLocation = this.netLocation;
        if (prevLocation!=netLocation) {
            if (this.prevLocations ==null){
                this.prevLocations = new ArrayList<>();
            }
            this.prevLocations.add(prevLocation);
            this.netLocation = netLocation;
        }
        return this;
    }

    public List<NetLocation> getPrevLocations() {
        return prevLocations;
    }

    public FakeContext getFakeContext() {
        return fakeContext;
    }

    public NetLocation getNetLocation() {
        return netLocation;
    }

    public EndPoint getSrc() {
        return src;
    }

    public EndPoint getDst() {
        return dst;
    }

    public EventLoop getEventLoop() {
        return eventLoop;
    }

    public String getTag() {
        return tag;
    }

    public boolean isClosed() {
        return close;
    }

    public ProxyContext closeHandler(Consumer<Void> closeHandler) {
        this.closeHandler = closeHandler;
        return this;
    }

    public Consumer<DatagramPacket> fallbackPacketHandler() {
        return fallbackPacketHandler;
    }

    public ProxyContext fallbackPacketHandler(Consumer<DatagramPacket> fallbackPacketHandler) {
        this.fallbackPacketHandler = fallbackPacketHandler;
        return this;
    }
}
