package io.crowds.proxy;

import io.netty.util.CharsetUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class DomainNetAddr  extends NetAddr{

    private String host;
    private int port;

    public DomainNetAddr(String host, int port) {
        super(InetSocketAddress.createUnresolved(host,port));
        this.host = host;
        this.port = port;
    }

    public DomainNetAddr(InetSocketAddress address){
        super(address);
        this.host=address.getHostString();
        this.port=address.getPort();

    }

    public NetAddr resolve(){
        return new NetAddr(new InetSocketAddress(host,port));
    }

    @Override
    public boolean isIpv4() {
        return false;
    }

    @Override
    public boolean isIpv6() {
        return false;
    }

    @Override
    public byte[] getByte() {
        return host.getBytes(CharsetUtil.US_ASCII);
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return host+":"+port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DomainNetAddr that = (DomainNetAddr) o;
        return port == that.port && Objects.equals(host, that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }
}
