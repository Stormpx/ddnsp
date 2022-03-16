package io.crowds.proxy.transport.proxy.vmess;

import io.crowds.proxy.transport.ProtocolOption;
import io.netty.handler.codec.http.HttpHeaders;

import java.net.InetSocketAddress;

public class VmessOption extends ProtocolOption {
    private InetSocketAddress address;
    private Security security;
    private User user;



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
