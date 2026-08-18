package io.crowds.dns.upstream;

import java.util.concurrent.TimeUnit;

public record LatencyPolicy(
    double alpha,               // EWMA smoothing factor, default 0.2
    double explorationRate,     // epsilon-greedy exploration probability, default 0.05
    int warmupSamples,          // samples per upstream before single-route, default 5
    int failureThreshold,       // consecutive failures to trip circuit breaker, default 3
    long cooldownNanos,         // breaker cooldown, default 10s
    int maxRetries,             // failover attempts, default 1
    int raceTopK                // race the k best upstreams, default 1
) {
    public static final LatencyPolicy DEFAULT = new LatencyPolicy(
        0.2, 0.05, 5, 3,
        TimeUnit.SECONDS.toNanos(10), 1,  1);
}
