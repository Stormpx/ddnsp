package io.crowds.proxy.transport.proxy.tuic;

import io.crowds.proxy.transport.ProtocolOption;

import java.net.InetSocketAddress;
import java.util.UUID;

public class TuicOption extends ProtocolOption {

    private InetSocketAddress address;
    private UUID uuid;
    private String password;
    private UdpMode udpMode;

    public TuicOption() {
    }

    public TuicOption(TuicOption other) {
        super(other);
        this.address = other.address;
        this.uuid = other.uuid;
        this.password = other.password;
        this.udpMode = other.udpMode;
    }

    public User getUser(){
        return new User(uuid,password);
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public TuicOption setAddress(InetSocketAddress address) {
        this.address = address;
        return this;
    }

    public UUID getUuid() {
        return uuid;
    }

    public TuicOption setUuid(UUID uuid) {
        this.uuid = uuid;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public TuicOption setPassword(String password) {
        this.password = password;
        return this;
    }

    public UdpMode getUdpMode() {
        return udpMode;
    }

    public TuicOption setUdpMode(UdpMode udpMode) {
        this.udpMode = udpMode;
        return this;
    }
}
