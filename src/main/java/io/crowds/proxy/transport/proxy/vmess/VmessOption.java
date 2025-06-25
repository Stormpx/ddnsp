package io.crowds.proxy.transport.proxy.vmess;

import io.crowds.proxy.transport.ProtocolOption;

import java.net.InetSocketAddress;

public class VmessOption extends ProtocolOption {
    private InetSocketAddress address;
    private Security security;
    private User user;

    public VmessOption() {
    }

    public VmessOption(VmessOption other) {
        super(other);
        this.address = other.address;
        this.security = other.security;
        this.user = other.user==null?null:other.user;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public VmessOption setAddress(InetSocketAddress address) {
        this.address = address;
        return this;
    }

    public Security getSecurity() {
        return security;
    }

    public VmessOption setSecurity(Security security) {
        this.security = security;
        return this;
    }

    public User getUser() {
        return user;
    }

    public VmessOption setUser(User user) {
        this.user = user;
        return this;
    }

}
