package io.crowds.dns;

import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsResponse;
import io.vertx.core.Future;

public interface DnsUpstream {


    Future<DnsResponse> lookup(DnsQuery query);

}
