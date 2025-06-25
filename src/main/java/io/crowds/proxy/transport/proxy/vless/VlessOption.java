package io.crowds.proxy.transport.proxy.vless;

import io.crowds.proxy.transport.ProtocolOption;

import java.net.InetSocketAddress;
import java.util.UUID;

public class VlessOption extends ProtocolOption {
    private InetSocketAddress address;
    private String id;

    public VlessOption() {
    }

    public VlessOption(VlessOption other) {
        super(other);
        this.address = other.address;
        this.id = other.id;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public VlessOption setAddress(InetSocketAddress address) {
        this.address = address;
        return this;
    }

    public UUID getUUID(){
        return VlessUUID.of(id);
    }

    public String getId() {
        return id;
    }

    public VlessOption setId(String id) {
        this.id = id;
        return this;
    }
}
