package io.crowds.compoments.wireguard;

import io.crowds.util.IPCIDR;

import java.net.InetSocketAddress;

public record PeerOption(String publicKey, String perSharedKey,
                         IPCIDR allowedIp,
                         short keepAlive,
                         InetSocketAddress endpoint) {
}
