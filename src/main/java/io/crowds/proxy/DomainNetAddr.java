package io.crowds.proxy;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;

public class DomainNetAddr  extends NetAddr{

    private String host;
    private int port;

    public DomainNetAddr(String host, int port) {
        super(InetSocketAddress.createUnresolved(host,port));
        this.host = host;
        this.port = port;
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
        return host.getBytes();
    }

    @Override
    public int getPort() {
        return port;
    }
}
