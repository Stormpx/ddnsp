package io.crowds.proxy;

import io.crowds.proxy.dns.FakeContext;
import io.crowds.proxy.transport.EndPoint;
import io.netty.channel.EventLoop;

public class ProxyContext {
    private EventLoop eventLoop;
    private EndPoint src;
    private EndPoint dest;
    private NetLocation netLocation;

    private String tag;
    private FakeContext fakeContext;

    public ProxyContext(EventLoop eventLoop, NetLocation netLocation) {
        this.eventLoop = eventLoop;
        this.netLocation = netLocation;
    }

    public ProxyContext(NetLocation netLocation) {
        this.netLocation=netLocation;
    }

    public void bridging(EndPoint src,EndPoint dest){
        src.bufferHandler(dest::write);
        dest.bufferHandler(src::write);
        src.writabilityHandler(dest::setAutoRead);
        dest.writabilityHandler(src::setAutoRead);
        src.closeFuture().addListener(closeFuture->{
            dest.close();
        });
        dest.closeFuture().addListener(closeFuture->{
            src.close();
        });
        this.src=src;
        this.dest=dest;
    }

    public ProxyContext withFakeContext(FakeContext fakeContext) {
        if (fakeContext!=null) {
            this.fakeContext = fakeContext;
            this.netLocation = new NetLocation(netLocation.getSrc(), fakeContext.getNetAddr(netLocation.getDest().getPort()), netLocation.getTp());
        }
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


}
