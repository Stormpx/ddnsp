package io.crowds.proxy.transport.proxy.socks;

import io.crowds.proxy.transport.ProtocolOption;

import java.net.InetSocketAddress;

public class SocksOption extends ProtocolOption {

    private InetSocketAddress remote;

    public SocksOption() {
    }

    public SocksOption(SocksOption other) {
        super(other);
        this.remote = other.remote;
    }

    public InetSocketAddress getRemote() {
        return remote;
    }

    public SocksOption setRemote(InetSocketAddress remote) {
        this.remote = remote;
        return this;
    }
}
