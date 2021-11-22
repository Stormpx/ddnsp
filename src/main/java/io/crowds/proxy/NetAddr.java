package io.crowds.proxy;

import java.net.*;
import java.util.Objects;

public class NetAddr {

    private SocketAddress address;

    public NetAddr(SocketAddress address) {
        this.address = address;
    }

    public SocketAddress getAddress() {
        return address;
    }

    public InetSocketAddress getAsInetAddr(){
        return (InetSocketAddress) address;
    }

    public boolean isIpv4(){
        return getAsInetAddr().getAddress() instanceof Inet4Address;
    }

    public boolean isIpv6(){
        return getAsInetAddr().getAddress() instanceof Inet6Address;
    }

    public byte[] getByte(){
        return getAsInetAddr().getAddress().getAddress();
    }

    public String getHost(){return getAsInetAddr().getHostName();}

    public int getPort(){
        return getAsInetAddr().getPort();
    }

    @Override
    public String toString() {
        return address.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NetAddr netAddr = (NetAddr) o;
        return Objects.equals(address, netAddr.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }
}
