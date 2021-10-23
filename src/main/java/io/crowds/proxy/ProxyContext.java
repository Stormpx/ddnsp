package io.crowds.proxy;

import io.crowds.proxy.transport.EndPoint;

public class ProxyContext {

    private EndPoint src;
    private EndPoint dest;
    private NetLocation netLocation;

    public ProxyContext(EndPoint src, EndPoint dest,NetLocation netLocation) {
        this.src = src;
        this.dest = dest;
        this.netLocation=netLocation;
        src.closeFuture().addListener(closeFuture->{
            dest.close();
        });
        dest.closeFuture().addListener(closeFuture->{
            src.close();
        });
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
