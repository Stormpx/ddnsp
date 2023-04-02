package io.crowds.dns;

import io.netty.channel.EventLoopGroup;

import java.net.URI;
import java.util.List;

public class ClientOption {
    private List<URI> upstreams;

    private boolean tryIpv6;

    public List<URI> getUpstreams() {
        return upstreams;
    }

    public ClientOption setUpstreams(List<URI> upstreams) {
        this.upstreams = upstreams;
        return this;
    }

    public boolean isTryIpv6() {
        return tryIpv6;
    }

    public ClientOption setTryIpv6(boolean tryIpv6) {
        this.tryIpv6 = tryIpv6;
        return this;
    }
}
