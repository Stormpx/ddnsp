package io.crowds.proxy;

import io.netty.channel.Channel;

import java.net.SocketAddress;
import java.util.function.Consumer;

public class DatagramOption {

    private boolean ipTransport=false;
    private SocketAddress bindAddr;
    private int idleTimeout=60;
    private Consumer<Channel> onIdle;



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

    public Consumer<Channel> getOnIdle() {
        return onIdle;
    }

    public DatagramOption setOnIdle(Consumer<Channel> onIdle) {
        this.onIdle = onIdle;
        return this;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    public DatagramOption setIdleTimeout(int idleTimeout) {
        this.idleTimeout = idleTimeout;
        return this;
    }
}
