package io.crowds.tun.wireguard;

import io.crowds.tun.TunOption;

import java.util.List;

public class WireGuardOption extends TunOption {


    private String privateKey;
    private List<PeerOption> peers;



    public String getPrivateKey() {
        return privateKey;
    }

    public WireGuardOption setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    public List<PeerOption> getPeers() {
        return peers;
    }

    public WireGuardOption setPeers(List<PeerOption> peers) {
        this.peers = peers;
        return this;
    }
}
