package io.crowds.proxy.transport.proxy.vless;

import io.crowds.proxy.transport.ProtocolOption;

import java.net.InetSocketAddress;
import java.util.UUID;

public class VlessOption extends ProtocolOption {
    private InetSocketAddress address;
    private UUID id;

    public InetSocketAddress getAddress() {
        return address;
    }

    public VlessOption setAddress(InetSocketAddress address) {
        this.address = address;
        return this;
    }

    public UUID getId() {
        return id;
    }

    public VlessOption setId(UUID id) {
        this.id = id;
        return this;
    }
}
