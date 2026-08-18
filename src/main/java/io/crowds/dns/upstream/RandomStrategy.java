package io.crowds.dns.upstream;

import io.crowds.dns.SafeDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsResponse;
import io.vertx.core.Future;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomStrategy implements DnsUpstreamStrategy{
    private final List<DnsUpstream> upstreams;

    public RandomStrategy(List<DnsUpstream> upstreams) {
        this.upstreams = upstreams;
    }

    @Override
    public Future<DnsResponse> lookup(DnsQuery query) {
        int idx = ThreadLocalRandom.current().nextInt(upstreams.size());
        return upstreams.get(idx).lookup(query).map(SafeDnsResponse::copy);
    }
}
