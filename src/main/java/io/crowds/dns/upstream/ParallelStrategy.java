package io.crowds.dns.upstream;

import io.crowds.dns.SafeDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ParallelStrategy implements DnsUpstreamStrategy{
    private final List<DnsUpstream> upstreams;

    public ParallelStrategy(List<DnsUpstream> upstreams) {
        this.upstreams = upstreams;
    }
    @Override
    public Future<DnsResponse> lookup(DnsQuery query) {
        if (upstreams.isEmpty()) {
            return Future.failedFuture(new RuntimeException("no available upstream"));
        }
        int n = upstreams.size();
        if (n == 1) {
            return upstreams.getFirst().lookup(query).map(SafeDnsResponse::copy);
        }
        Promise<DnsResponse> promise = Promise.promise();
        AtomicInteger pending = new AtomicInteger(n);
        for (DnsUpstream u : upstreams) {
            u.lookup(query).onComplete(ar -> {
                try {
                    if (ar.succeeded()) {
                        DnsResponse result = ar.result();
                        if (!promise.tryComplete(result)){
                            ReferenceCountUtil.safeRelease(ar.result());
                        }
                    }
                } finally {
                    if (pending.decrementAndGet() == 0) {
                        if (!promise.future().succeeded()){
                            promise.tryFail(new RuntimeException("all upstreams failed"));
                        }
                    }
                }
            });
        }
        return promise.future().map(SafeDnsResponse::copy);
    }
}
