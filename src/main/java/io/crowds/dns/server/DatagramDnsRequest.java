package io.crowds.dns.server;

import io.netty.channel.Channel;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;

public class DatagramDnsRequest implements DnsRequest{
    private final Channel channel;
    private final DatagramDnsQuery dnsQuery;

    public DatagramDnsRequest(Channel channel, DatagramDnsQuery dnsQuery) {
        this.channel = channel;
        this.dnsQuery = dnsQuery;
    }

    @Override
    public InetSocketAddress sender() {
        return dnsQuery.sender();
    }

    @Override
    public DnsQuery query() {
        return dnsQuery;
    }

    @Override
    public DnsResponse newResponse() {
        return new DatagramDnsResponse(dnsQuery.recipient(),dnsQuery.sender(),0);
    }

    @Override
    public void response(DnsResponse response) {
        channel.writeAndFlush(response)
               .addListener(f-> ReferenceCountUtil.safeRelease(this));
    }
}
