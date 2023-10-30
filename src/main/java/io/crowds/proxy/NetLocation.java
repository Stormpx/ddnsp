package io.crowds.proxy;

import java.net.SocketAddress;
import java.util.Objects;

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

    public NetAddr getDst() {
        return dest;
    }




    public TP getTp() {
        return tp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NetLocation that = (NetLocation) o;
        return Objects.equals(src, that.src) && Objects.equals(dest, that.dest) && tp == that.tp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(src, dest, tp);
    }

    @Override
    public String toString() {
        return "NetLocation{" + "src=" + src + ", dest=" + dest + ", tp=" + tp + '}';
    }
}
