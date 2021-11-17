package io.crowds.proxy.dns;

import java.net.InetAddress;

public record RealAddr(long ttl,InetAddress addr) {

}
