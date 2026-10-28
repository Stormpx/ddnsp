package io.crowds.proxy.transport.proxy.wisp;

import io.crowds.proxy.transport.ProtocolOption;

import java.net.InetSocketAddress;

public class WispOption extends ProtocolOption {
    private InetSocketAddress address;
    private String username;
    private String password;
    private String privateKey;
    private String privateKeyHex;

    public WispOption() {
    }

    public WispOption(WispOption other) {
        super(other);
        this.address = other.address;
        this.username = other.username;
        this.password = other.password;
        this.privateKey = other.privateKey;
        this.privateKeyHex = other.privateKeyHex;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public WispOption setAddress(InetSocketAddress address) {
        this.address = address;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public WispOption setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public WispOption setPassword(String password) {
        this.password = password;
        return this;
    }

    /**
     * path of the ed25519 private key which is used for the public/private key authentication.
     * both pem encoded pkcs#8 and raw pkcs#8 der are supported.
     */
    public String getPrivateKey() {
        return privateKey;
    }

    public WispOption setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    /**
     * hex encoded ed25519 private key, either a 32 bytes seed or a 64 bytes seed+publicKey
     */
    public String getPrivateKeyHex() {
        return privateKeyHex;
    }

    public WispOption setPrivateKeyHex(String privateKeyHex) {
        this.privateKeyHex = privateKeyHex;
        return this;
    }
}
