package io.crowds.proxy;

import io.netty.channel.EventLoop;

import java.net.SocketAddress;

public class DatagramOption {

    private boolean ipTransport=false;
    private SocketAddress bindAddr;

    private EventLoop eventLoop;

    public boolean isIpTransparent() {
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

    public EventLoop getEventLoop() {
        return eventLoop;
    }

    public DatagramOption setEventLoop(EventLoop eventLoop) {
        this.eventLoop = eventLoop;
        return this;
    }
}
