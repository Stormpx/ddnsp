package io.crowds.dns.upstream;

import io.crowds.dns.SafeDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Ticker;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Predicate;

public class LatencyBasedStrategy implements DnsUpstreamStrategy{

    private final Ticker ticker = Ticker.systemTicker();
    private final LatencyPolicy policy;
    private final List<Upstream> upstreams;

    public LatencyBasedStrategy( LatencyPolicy policy,List<DnsUpstream> upstreams) {
        this.policy = policy;
        this.upstreams = upstreams.stream().map(Upstream::new).toList();
    }


    private final class Upstream{
        private final static AtomicReferenceFieldUpdater<Upstream,Stat> STAT_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(Upstream.class,Stat.class,"stat");
        private final DnsUpstream inner;
        private volatile Stat stat;

        record Stat(double latency, int samples, int failures, long lastUpdated){ }

        private Upstream(DnsUpstream inner) {
            this.inner = inner;
            this.stat = new Stat(Long.MAX_VALUE,0,0,ticker.initialNanoTime());
        }

        // Record one attempt
        void record(long rtt, boolean success, long now) {
            STAT_UPDATER.getAndUpdate(this,stat->{
                double latency = stat.samples == 0 ? rtt : stat.latency + policy.alpha() * (rtt - stat.latency);
                return new Stat(latency,stat.samples + 1,success ? 0 : stat.failures + 1,now);
            });
        }

        // Healthy if below failure threshold, or cooldown elapsed since last update.
        boolean isHealthy(long now) {
            Stat stat = this.stat;
            return stat.failures < policy.failureThreshold()
                || now - stat.lastUpdated >= policy.cooldownNanos();
        }


        Future<DnsResponse> lookup(DnsQuery query){
            long start = ticker.nanoTime();
            return inner.lookup(query)
                        .onComplete(ar->{
                            boolean success = ar.succeeded();
                            long now = ticker.nanoTime();
                            long rtt = now - start;
                            record(rtt, success, now);
                        });
        }
    }

    private Future<DnsResponse> lookup(List<Upstream> upstreams, DnsQuery query) {
        if (upstreams.isEmpty()) {
            return Future.failedFuture(new RuntimeException("no available upstream"));
        }
        int n = upstreams.size();
        if (n == 1) {
            return upstreams.getFirst().lookup(query).map(SafeDnsResponse::copy);
        }
        Promise<DnsResponse> promise = Promise.promise();
        AtomicInteger pending = new AtomicInteger(n);
        for (Upstream upstream : upstreams) {
            upstream.lookup(query).onComplete(ar -> {
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

    private Future<DnsResponse> retry(Throwable t, Set<Upstream> excludes, int retry, DnsQuery query){
        if (retry >= policy.maxRetries() || excludes.size() >= upstreams.size()){
            return Future.failedFuture(t);
        }
        long now = ticker.nanoTime();
        var upstream = upstreams.stream()
                                .filter(it->it.isHealthy(now))
                                .filter(Predicate.not(excludes::contains))
                                .min(Comparator.comparingDouble(it -> it.stat.latency))
                                .orElse(null);
        if (upstream==null){
            return Future.failedFuture(t);
        }
        excludes.add(upstream);
        return upstream.lookup(query)
                       .recover(throwable -> retry(throwable,excludes,retry + 1, query))
                       .map(SafeDnsResponse::copy);
    }

    @Override
    public Future<DnsResponse> lookup(DnsQuery query) {

        boolean needWarmup = upstreams.stream().anyMatch(it -> it.stat.samples < policy.warmupSamples());
        if (needWarmup){
            return lookup(upstreams, query);
        }
        long now = ticker.nanoTime();
        List<Upstream> healthy = upstreams.stream().filter(it -> it.isHealthy(now)).toList();
        if (healthy.isEmpty()){
            return lookup(upstreams, query);
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() < policy.explorationRate()){
            Upstream upstream = healthy.get(random.nextInt(healthy.size()));
            return upstream.lookup(query)
                           .recover(throwable -> retry(throwable,new HashSet<>(List.of(upstream)),0, query))
                           .map(SafeDnsResponse::copy);
        }

        var ups = healthy.stream()
                         .sorted(Comparator.comparingDouble(it -> it.stat.latency))
                         .limit(policy.raceTopK())
                         .toList();
        if (ups.isEmpty()){
            return lookup(upstreams, query);
        }

        return lookup(ups, query).recover(throwable -> retry(throwable,new HashSet<>(ups),0, query));
    }
}
