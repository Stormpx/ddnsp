package dns;

import io.crowds.dns.upstream.LatencyBasedStrategy;
import io.crowds.dns.upstream.LatencyPolicy;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LatencyBasedStrategyTest {

    private ScheduledExecutorService scheduler;

    @Before
    public void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @After
    public void tearDown() {
        scheduler.shutdownNow();
    }

    // ---------- helpers ----------

    private static final long LONG_COOLDOWN_MS = TimeUnit.HOURS.toMillis(1);
    /** Slowest mock delay used in tests is 60ms; settle must exceed it so samples land. */
    private static final long SETTLE_MS = 120;

    private static LatencyPolicy policy(double exploration, int warmup, int threshold,
                                        long cooldownMs, int maxRetries, int raceTopK) {
        return new LatencyPolicy(
                0.2, exploration, warmup, threshold,
                TimeUnit.MILLISECONDS.toNanos(cooldownMs), maxRetries, raceTopK);
    }

    private static LatencyPolicy defaultPolicy() {
        return policy(0.0, 2, 2, LONG_COOLDOWN_MS, 1, 1);
    }

    private static DnsQuery newQuery() {
        DefaultDnsQuery query = new DefaultDnsQuery(1, DnsOpCode.QUERY);
        query.setRecursionDesired(true);
        query.addRecord(DnsSection.QUESTION, new DefaultDnsQuestion("example.com.", DnsRecordType.A));
        return query;
    }

    /** Block until the future completes; returns the same future (failed or succeeded). */
    private static <T> Future<T> block(Future<T> future) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        future.onComplete(ar -> latch.countDown());
        latch.await(5, TimeUnit.SECONDS);
        return future;
    }

    /** Block until the lookup completes, assert success, return the response (caller releases). */
    private static DnsResponse awaitSuccess(LatencyBasedStrategy strategy) throws Exception {
        Future<DnsResponse> f = block(strategy.lookup(newQuery()));
        assertTrue(f.succeeded());
        return f.result();
    }

    /**
     * Wait for every in-flight mock lookup to record its sample.  The mock
     * completes slow upstreams later than the winner, and the strategy only
     * exits warmup once ALL upstreams have {@code warmupSamples} samples, so
     * tests must let the slowest sample land before issuing the next query.
     */
    private static void settle() {
        try {
            Thread.sleep(SETTLE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static void release(DnsResponse response) {
        ReferenceCountUtil.safeRelease(response);
    }

    // ---------- tests ----------

    @Test
    public void warmupRacesAllUpstreams() throws Exception {
        MockUpstream a = new MockUpstream("a", scheduler).behavior(2, false);
        MockUpstream b = new MockUpstream("b", scheduler).behavior(30, false);
        LatencyBasedStrategy strategy = new LatencyBasedStrategy(defaultPolicy(), List.of(a, b));

        release(awaitSuccess(strategy)); // warmup query 1
        settle();
        release(awaitSuccess(strategy)); // warmup query 2 -> both have 2 samples, warmup done
        settle();

        assertEquals(2, a.calls());
        assertEquals(2, b.calls());

        release(awaitSuccess(strategy)); // post-warmup: single route to the fastest
        settle();

        assertEquals(3, a.calls());
        assertEquals(2, b.calls());
    }

    @Test
    public void routesToFastestAfterWarmup() throws Exception {
        MockUpstream a = new MockUpstream("a", scheduler).behavior(2, false);
        MockUpstream b = new MockUpstream("b", scheduler).behavior(30, false);
        LatencyBasedStrategy strategy = new LatencyBasedStrategy(defaultPolicy(), List.of(a, b));

        for (int i = 0; i < 7; i++) {
            release(awaitSuccess(strategy));
            settle();
        }

        assertEquals(7, a.calls());
        assertEquals(2, b.calls()); // warmup only; all routed queries go to a
    }

    @Test
    public void failsOverToNextBestOnFailure() throws Exception {
        MockUpstream a = new MockUpstream("a", scheduler).behavior(2, false);
        MockUpstream b = new MockUpstream("b", scheduler).behavior(30, false);
        LatencyBasedStrategy strategy = new LatencyBasedStrategy(defaultPolicy(), List.of(a, b));

        release(awaitSuccess(strategy));
        settle();
        release(awaitSuccess(strategy)); // warmup
        settle();

        a.behavior(2, true); // fastest upstream now fails

        Future<DnsResponse> f = block(strategy.lookup(newQuery()));
        assertTrue(f.succeeded()); // recovered via retry on b
        assertNotNull(f.result());
        release(f.result());
        settle();

        assertEquals(3, a.calls()); // 2 warmup + 1 failed selection
        assertEquals(3, b.calls()); // 2 warmup + 1 retry
        assertEquals(1, a.failures());
    }

    @Test
    public void maxRetriesZeroDisablesFailover() throws Exception {
        LatencyPolicy p = policy(0.0, 2, 2, LONG_COOLDOWN_MS, 0, 1);
        MockUpstream a = new MockUpstream("a", scheduler).behavior(2, false);
        MockUpstream b = new MockUpstream("b", scheduler).behavior(30, false);
        LatencyBasedStrategy strategy = new LatencyBasedStrategy(p, List.of(a, b));

        release(awaitSuccess(strategy));
        settle();
        release(awaitSuccess(strategy)); // warmup
        settle();

        a.behavior(2, true);

        Future<DnsResponse> f = block(strategy.lookup(newQuery()));
        assertTrue(f.failed()); // no failover allowed
        settle();

        assertEquals(3, a.calls());
        assertEquals(2, b.calls()); // never tried
    }

    @Test
    public void maxRetriesBoundsFailoverChain() throws Exception {
        // maxRetries=1: a fails -> retry b -> b fails -> give up, c never tried
        MockUpstream a1 = new MockUpstream("a1", scheduler).behavior(2, false);
        MockUpstream b1 = new MockUpstream("b1", scheduler).behavior(2, false);
        MockUpstream c1 = new MockUpstream("c1", scheduler).behavior(60, false);
        LatencyBasedStrategy limited = new LatencyBasedStrategy(
                policy(0.0, 2, 2, LONG_COOLDOWN_MS, 1, 1), List.of(a1, b1, c1));

        release(awaitSuccess(limited));
        settle();
        release(awaitSuccess(limited)); // warmup
        settle();

        a1.behavior(2, true);
        b1.behavior(2, true);

        Future<DnsResponse> f1 = block(limited.lookup(newQuery()));
        assertTrue(f1.failed());
        settle();
        assertEquals(3, a1.calls());
        assertEquals(3, b1.calls());
        assertEquals(2, c1.calls()); // warmup only

        // maxRetries=2: a fails -> b fails -> c succeeds
        MockUpstream a2 = new MockUpstream("a2", scheduler).behavior(2, false);
        MockUpstream b2 = new MockUpstream("b2", scheduler).behavior(2, false);
        MockUpstream c2 = new MockUpstream("c2", scheduler).behavior(60, false);
        LatencyBasedStrategy generous = new LatencyBasedStrategy(
                policy(0.0, 2, 2, LONG_COOLDOWN_MS, 2, 1), List.of(a2, b2, c2));

        release(awaitSuccess(generous));
        settle();
        release(awaitSuccess(generous)); // warmup
        settle();

        a2.behavior(2, true);
        b2.behavior(2, true);

        Future<DnsResponse> f2 = block(generous.lookup(newQuery()));
        assertTrue(f2.succeeded());
        release(f2.result());
        settle();
        assertEquals(3, a2.calls());
        assertEquals(3, b2.calls());
        assertEquals(3, c2.calls()); // warmup 2 + 1 retry
    }

    @Test
    public void circuitBreakerExcludesFailingUpstreamAndFallsBackWhenAllUnhealthy() throws Exception {
        MockUpstream a = new MockUpstream("a", scheduler).behavior(2, true);
        MockUpstream b = new MockUpstream("b", scheduler).behavior(30, true);
        LatencyBasedStrategy strategy = new LatencyBasedStrategy(defaultPolicy(), List.of(a, b));

        Future<DnsResponse> f1 = block(strategy.lookup(newQuery()));
        assertTrue(f1.failed()); // warmup race: both fail
        settle();
        Future<DnsResponse> f2 = block(strategy.lookup(newQuery()));
        assertTrue(f2.failed());
        settle();

        assertEquals(2, a.calls());
        assertEquals(2, b.calls());
        assertEquals(2, a.failures());
        assertEquals(2, b.failures());

        // warmup done, both unhealthy -> healthy empty -> fallback to full race
        Future<DnsResponse> f3 = block(strategy.lookup(newQuery()));
        assertTrue(f3.failed());
        settle();

        assertEquals(3, a.calls());
        assertEquals(3, b.calls());
    }

    @Test
    public void cooldownRecoversBreaker() throws Exception {
        LatencyPolicy p = policy(0.0, 1, 1, 300, 1, 1);
        MockUpstream a = new MockUpstream("a", scheduler).behavior(2, true);
        MockUpstream b = new MockUpstream("b", scheduler).behavior(30, false);
        LatencyBasedStrategy strategy = new LatencyBasedStrategy(p, List.of(a, b));

        release(awaitSuccess(strategy)); // warmup race: a fails, b succeeds
        settle();
        assertEquals(1, a.failures());

        release(awaitSuccess(strategy)); // selection: a breaker'd -> b
        settle();
        assertEquals(1, a.calls());
        assertEquals(2, b.calls());

        // a recovers; after cooldown it is healthy again and fastest
        a.behavior(2, false);
        Thread.sleep(400);

        release(awaitSuccess(strategy));
        settle();
        assertEquals(2, a.calls());
        assertEquals(2, b.calls());
    }

    @Test
    public void servfailIsTreatedAsSuccess() throws Exception {
        MockUpstream a = new MockUpstream("a", scheduler).behavior(2, false, DnsResponseCode.SERVFAIL);
        MockUpstream b = new MockUpstream("b", scheduler).behavior(30, false);
        LatencyBasedStrategy strategy = new LatencyBasedStrategy(defaultPolicy(), List.of(a, b));

        release(awaitSuccess(strategy));
        settle();
        release(awaitSuccess(strategy)); // warmup
        settle();

        assertEquals(0, a.failures()); // SERVFAIL is a valid response, not a failure

        Future<DnsResponse> f = block(strategy.lookup(newQuery()));
        assertTrue(f.succeeded());
        assertEquals(DnsResponseCode.SERVFAIL, f.result().code());
        release(f.result());
        settle();

        assertEquals(3, a.calls()); // returned as-is, no retry
        assertEquals(2, b.calls());
    }

    @Test
    public void explorationSpreadsQueries() throws Exception {
        LatencyPolicy p = policy(1.0, 1, 2, LONG_COOLDOWN_MS, 1, 1);
        MockUpstream a = new MockUpstream("a", scheduler).behavior(2, false);
        MockUpstream b = new MockUpstream("b", scheduler).behavior(30, false);
        LatencyBasedStrategy strategy = new LatencyBasedStrategy(p, List.of(a, b));

        release(awaitSuccess(strategy)); // warmup race (1 query, both sampled)
        settle(); // let b's sample land -> warmup done

        for (int i = 0; i < 29; i++) {
            release(awaitSuccess(strategy)); // exploration picks one random upstream
        }

        assertEquals(31, a.calls() + b.calls()); // 1 warmup (both) + 29 single picks
        assertTrue("a should be picked often", a.calls() > 5);
        assertTrue("b should be picked often", b.calls() > 5);
    }

    @Test
    public void singleUpstreamWorks() throws Exception {
        MockUpstream a = new MockUpstream("a", scheduler).behavior(2, false);
        LatencyBasedStrategy strategy = new LatencyBasedStrategy(defaultPolicy(), List.of(a));

        release(awaitSuccess(strategy));
        assertEquals(1, a.calls());
    }

    @Test
    public void concurrentQueriesAreSafe() throws Exception {
        MockUpstream a = new MockUpstream("a", scheduler).behavior(2, false);
        MockUpstream b = new MockUpstream("b", scheduler).behavior(30, false);
        LatencyBasedStrategy strategy = new LatencyBasedStrategy(defaultPolicy(), List.of(a, b));

        release(awaitSuccess(strategy));
        settle();
        release(awaitSuccess(strategy)); // warmup done
        settle();

        List<Future<DnsResponse>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(strategy.lookup(newQuery()));
        }
        for (Future<DnsResponse> f : futures) {
            Future<DnsResponse> done = block(f);
            assertTrue(done.succeeded());
            release(done.result());
        }

        assertEquals(22, a.calls()); // 2 warmup + 20 routed
        assertEquals(2, b.calls());
    }
}
