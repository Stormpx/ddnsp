package io.crowds.dns.server;

import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;

public class SocketDnsRequest implements DnsRequest {
    private final SocketChannel channel;
    private final DefaultDnsQuery dnsQuery;

    public SocketDnsRequest(SocketChannel channel, DefaultDnsQuery dnsQuery) {
        this.channel = channel;
        this.dnsQuery = dnsQuery;
    }

    @Override
    public InetSocketAddress sender() {
        return channel.remoteAddress();
    }

    @Override
    public DnsQuery query() {
        return dnsQuery;
    }

    @Override
    public DnsResponse newResponse() {
        return new DefaultDnsResponse(0);
    }

    @Override
    public void response(DnsResponse response) {
        this.channel.writeAndFlush(response)
                    .addListener(f-> ReferenceCountUtil.safeRelease(this));
    }
}
