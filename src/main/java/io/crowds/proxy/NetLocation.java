package io.crowds.proxy;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class NetLocation {
    private NetAddr src;
    private NetAddr dest;
    private TP tp;

    public NetLocation(SocketAddress src, SocketAddress dest, TP tp) {
        this.src = new NetAddr(src);
        this.dest = new NetAddr(dest);
        this.tp = tp;
    }

    public NetLocation(NetAddr src, NetAddr dest, TP tp) {
        this.src = src;
        this.dest = dest;
        this.tp = tp;
    }

    public NetAddr getSrc() {
        return src;
    }

    public NetAddr getDest() {
        return dest;
    }




    public TP getTp() {
        return tp;
    }
}
