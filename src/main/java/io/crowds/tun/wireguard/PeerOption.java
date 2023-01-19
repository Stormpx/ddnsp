package io.crowds.tun.wireguard;

import io.crowds.util.IPCIDR;

import java.net.InetSocketAddress;

public record PeerOption(String publicKey, String perSharedKey,
                         IPCIDR allowedIp,
                         short keepAlive,
                         InetSocketAddress endpointAddr) {
}
