package io.crowds.proxy;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

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
}
