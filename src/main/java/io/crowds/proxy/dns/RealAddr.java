package io.crowds.proxy.dns;

import java.net.InetAddress;

public class RealAddr {
    private long ttl;
    private InetAddress addr;

    public RealAddr(long ttl, InetAddress addr) {
        this.ttl = ttl;
        this.addr = addr;
    }

    public long getTtl() {
        return ttl;
    }

    public InetAddress getAddr() {
        return addr;
    }
}
