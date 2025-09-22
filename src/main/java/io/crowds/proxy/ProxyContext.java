package io.crowds.proxy;

import io.crowds.proxy.dns.FakeContext;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.TcpEndPoint;
import io.crowds.util.Exceptions;
import io.netty.channel.EventLoop;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
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

    private EventLoop eventLoop;
    private EndPoint src;
    private EndPoint dst;
    private final NetLocation netLocation;

    private String tag;
    private FakeContext fakeContext;

    private Consumer<DatagramPacket> fallbackPacketHandler;

    private boolean close;
    private Consumer<Void> closeHandler;

    public ProxyContext(EventLoop eventLoop, NetLocation netLocation) {
        this.eventLoop = eventLoop;
        this.netLocation = netLocation;
    }

    public ProxyContext(NetLocation netLocation) {
        this.netLocation=netLocation;
    }

    private boolean isSpliceAvailable(EndPoint src,EndPoint dst){
//        return false;
        return src instanceof TcpEndPoint && dst instanceof TcpEndPoint
                && src.channel() instanceof EpollSocketChannel srcSocket
                && dst.channel() instanceof EpollSocketChannel dstSocket
                && srcSocket.hasAttr(SEND_ZC_SUPPORTED)
                && dstSocket.hasAttr(SEND_ZC_SUPPORTED)
                && srcSocket.eventLoop()==dstSocket.eventLoop();
    }

    public void bridging(EndPoint src,EndPoint dst){
        dst.bufferHandler(src::write);
        src.bufferHandler(dst::write);
        src.writabilityHandler(dst::setAutoRead);
        dst.writabilityHandler(src::setAutoRead);
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
    }

    private void fireClose(){
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
