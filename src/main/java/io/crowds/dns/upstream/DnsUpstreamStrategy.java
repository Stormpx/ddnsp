package io.crowds.dns.upstream;

import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsResponse;
import io.vertx.core.Future;

import java.util.List;

/**
 * Strategy for scheduling a DNS query across a set of upstream servers.
 *
 * <p>Implementations hold their own upstream list ({@link List}&lt;{@link DnsUpstream}&gt;)

 *
 * <p>Contract:</p>
 * <ul>
 *   <li>The returned {@link DnsResponse} is owned exclusively by the caller,
 *       which is responsible for releasing it (reference count to zero);</li>
 *   <li>In concurrent scenarios, every response other than the winner must be
 *       released explicitly inside the strategy to avoid reference-count leaks;</li>
 *   <li>Implementations must hold a non-null immutable snapshot of the
 *       upstreams and must not change it while queries are in flight.</li>
 * </ul>
 */
public interface DnsUpstreamStrategy {

    /**
     * Perform one upstream scheduling for the query and return the single
     * winning response.
     *
     * @param query the DNS query message; must not be modified
     * @return a future containing the winning upstream's response on success,
     *         or a failed future when all upstreams fail
     */
    Future<DnsResponse> lookup(DnsQuery query);
}
