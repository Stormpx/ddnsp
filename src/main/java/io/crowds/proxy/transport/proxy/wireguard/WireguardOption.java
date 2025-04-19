package io.crowds.proxy.transport.proxy.wireguard;

import io.crowds.compoments.wireguard.PeerOption;
import io.crowds.proxy.transport.ProtocolOption;
import org.stormpx.net.util.SubNet;

import java.util.List;

public class WireguardOption extends ProtocolOption {

    private SubNet address;
    private String privateKey;
    private List<PeerOption> peers;

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
}
