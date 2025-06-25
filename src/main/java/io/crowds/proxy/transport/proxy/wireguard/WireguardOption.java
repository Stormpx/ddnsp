package io.crowds.proxy.transport.proxy.wireguard;

import io.crowds.compoments.wireguard.PeerOption;
import io.crowds.proxy.transport.ProtocolOption;
import org.stormpx.net.util.SubNet;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class WireguardOption extends ProtocolOption {

    private String privateKey;
    private SubNet address;
    private InetSocketAddress dns;
    private List<PeerOption> peers;

    public WireguardOption() {
    }

    public WireguardOption(WireguardOption other) {
        super(other);
        this.privateKey=other.privateKey;
        this.address=other.address;
        this.dns=other.dns;
        this.peers=other.peers==null?null:new ArrayList<>(other.peers);
    }

    public SubNet getAddress() {
        return address;
    }

    public WireguardOption setAddress(SubNet address) {
        this.address = address;
        return this;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public WireguardOption setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    public List<PeerOption> getPeers() {
        return peers;
    }

    public WireguardOption setPeers(List<PeerOption> peers) {
        this.peers = peers;
        return this;
    }

    public InetSocketAddress getDns() {
        return dns;
    }

    public WireguardOption setDns(InetSocketAddress dns) {
        this.dns = dns;
        return this;
    }
}
