package io.crowds.proxy;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class NetLocation {
    private SocketAddress src;
    private SocketAddress dest;
    private TP tp;

    public NetLocation(SocketAddress src, SocketAddress dest, TP tp) {
        this.src = src;
        this.dest = dest;
        this.tp = tp;
    }

    public SocketAddress getSrc() {
        return src;
    }

    public SocketAddress getDest() {
        return dest;
    }

    public TP getTp() {
        return tp;
    }
}
