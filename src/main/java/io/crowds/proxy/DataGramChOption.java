package io.crowds.proxy;

import io.netty.channel.Channel;

import java.net.SocketAddress;
import java.util.function.Consumer;

public class DataGramChOption {

    private boolean ipTransport=false;
    private SocketAddress bindAddr;
    private Consumer<Channel> onIdle;



    public boolean isIpTransport() {
        return ipTransport;
    }

    public DataGramChOption setIpTransport(boolean ipTransport) {
        this.ipTransport = ipTransport;
        return this;
    }

    public SocketAddress getBindAddr() {
        return bindAddr;
    }

    public DataGramChOption setBindAddr(SocketAddress bindAddr) {
        this.bindAddr = bindAddr;
        return this;
    }

    public Consumer<Channel> getOnIdle() {
        return onIdle;
    }

    public DataGramChOption setOnIdle(Consumer<Channel> onIdle) {
        this.onIdle = onIdle;
        return this;
    }
}
