package dns;

import io.crowds.dns.SafeDnsResponse;
import io.crowds.dns.upstream.DnsUpstream;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Controllable test upstream.
 *
 * <p>Each lookup is completed asynchronously on the shared scheduler after a
 * configurable delay, either successfully (with a fresh ref-counted
 * {@link SafeDnsResponse}) or with a failure.  A default {@link Outcome} can
 * be set via {@link #behavior}, and individual outcomes can be queued via
 * {@link #script} to sequence a specific scenario.</p>
 */
public class MockUpstream implements DnsUpstream {

    /** What one lookup should do: delay in ms, fail or not, response code. */
    public record Outcome(long delayMs, boolean fail, DnsResponseCode code) {
        public Outcome(long delayMs, boolean fail) {
            this(delayMs, fail, DnsResponseCode.NOERROR);
        }
    }

    private final String name;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<Outcome> behavior = new AtomicReference<>(new Outcome(0, false));
    private final Queue<Outcome> script = new ConcurrentLinkedQueue<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger failures = new AtomicInteger();

    public MockUpstream(String name, ScheduledExecutorService scheduler) {
        this.name = name;
        this.scheduler = scheduler;
    }

    /** Set the default behavior for every lookup. */
    public MockUpstream behavior(long delayMs, boolean fail) {
        behavior.set(new Outcome(delayMs, fail));
        return this;
    }

    /** Set the default behavior for every lookup, with a custom response code. */
    public MockUpstream behavior(long delayMs, boolean fail, DnsResponseCode code) {
        behavior.set(new Outcome(delayMs, fail, code));
        return this;
    }

    /** Queue one scripted outcome; consumed once per lookup, in order. */
    public MockUpstream script(long delayMs, boolean fail) {
        script.add(new Outcome(delayMs, fail));
        return this;
    }

    public String name() {
        return name;
    }

    public int calls() {
        return calls.get();
    }

    public int failures() {
        return failures.get();
    }

    @Override
    public Future<DnsResponse> lookup(DnsQuery query) {
        Outcome polled = script.poll();
        final Outcome outcome = polled != null ? polled : behavior.get();
        calls.incrementAndGet();
        if (outcome.fail()) {
            failures.incrementAndGet();
        }
        Promise<DnsResponse> promise = Promise.promise();
        scheduler.schedule(() -> {
            if (outcome.fail()) {
                promise.fail(new RuntimeException(name + " lookup failed"));
            } else {
                promise.complete(new SafeDnsResponse(query.id(), query.opCode(), outcome.code()));
            }
        }, outcome.delayMs(), TimeUnit.MILLISECONDS);
        return promise.future();
    }
}
