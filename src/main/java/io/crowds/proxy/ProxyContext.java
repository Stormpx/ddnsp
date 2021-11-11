package io.crowds.proxy;

import io.crowds.proxy.dns.FakeContext;
import io.crowds.proxy.transport.EndPoint;

public class ProxyContext {

    private EndPoint src;
    private EndPoint dest;
    private NetLocation netLocation;
    private FakeContext fakeContext;

    public ProxyContext(NetLocation netLocation) {
        this.netLocation=netLocation;
    }

    public void bridging(EndPoint src,EndPoint dest){
        dest.bufferHandler(src::write);
        src.bufferHandler(dest::write);
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
}
