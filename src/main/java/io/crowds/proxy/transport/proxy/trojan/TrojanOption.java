package io.crowds.proxy.transport.proxy.trojan;

import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.util.Hash;
import org.bouncycastle.util.encoders.Hex;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class TrojanOption extends ProtocolOption {
    private InetSocketAddress address;
    private byte[] password;


    public InetSocketAddress getAddress() {
        return address;
    }

    public TrojanOption setAddress(InetSocketAddress address) {
        this.address = address;
        return this;
    }

    public byte[] getPassword() {
        return password;
    }

    public TrojanOption setPassword(String password) {
        this.password = Hex.toHexString(Hash.sha224(password.getBytes(StandardCharsets.US_ASCII))).getBytes();
        return this;
    }
}
