package io.crowds.proxy;

import io.crowds.proxy.dns.FakeContext;
import io.crowds.proxy.transport.EndPoint;
import io.netty.channel.EventLoop;

import java.util.function.Consumer;

public class ProxyContext {
    private EventLoop eventLoop;
    private EndPoint src;
    private EndPoint dest;
    private NetLocation netLocation;

    private String tag;
    private FakeContext fakeContext;

    private boolean close;
    private Consumer<Void> closeHandler;

    public ProxyContext(EventLoop eventLoop, NetLocation netLocation) {
        this.eventLoop = eventLoop;
        this.netLocation = netLocation;
    }

    public ProxyContext(NetLocation netLocation) {
        this.netLocation=netLocation;
    }

    public void bridging(EndPoint src,EndPoint dest){
        dest.bufferHandler(src::write);
        src.bufferHandler(dest::write);
        src.writabilityHandler(dest::setAutoRead);
        dest.writabilityHandler(src::setAutoRead);
        src.closeFuture().addListener(closeFuture->{
            fireClose();
            dest.close();
        });
        dest.closeFuture().addListener(closeFuture->{
            fireClose();
            src.close();
        });

        this.src=src;
        this.dest=dest;
        src.setAutoRead(true);
        dest.setAutoRead(true);
    }

    private void fireClose(){
        if (this.close)
            return;

        this.close=true;
        if (this.closeHandler!=null)
            this.closeHandler.accept(null);
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

    public EndPoint getDest() {
        return dest;
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
}
