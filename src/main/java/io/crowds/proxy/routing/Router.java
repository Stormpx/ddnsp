package io.crowds.proxy.routing;

import io.crowds.proxy.NetLocation;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public interface Router {

    String routing(NetLocation netLocation);
    String routing(InetSocketAddress src, String domain);
    String routingIp(InetAddress address, boolean dest);
    String routing(InetSocketAddress address,boolean dest);
}
