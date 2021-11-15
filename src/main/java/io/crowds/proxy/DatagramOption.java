package io.crowds.proxy;

import io.netty.channel.Channel;

import java.net.SocketAddress;
import java.util.function.Consumer;

public class DatagramOption {

    private boolean ipTransport=false;
    private SocketAddress bindAddr;



    public boolean isIpTransport() {
        return ipTransport;
    }

    public DatagramOption setIpTransport(boolean ipTransport) {
        this.ipTransport = ipTransport;
        return this;
    }

    public SocketAddress getBindAddr() {
        return bindAddr;
    }

    public DatagramOption setBindAddr(SocketAddress bindAddr) {
        this.bindAddr = bindAddr;
        return this;
    }

}
