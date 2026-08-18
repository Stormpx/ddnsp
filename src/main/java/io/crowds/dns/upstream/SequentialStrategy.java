package io.crowds.dns.upstream;

import io.crowds.dns.SafeDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsResponse;
import io.vertx.core.Future;

import java.util.List;

public class SequentialStrategy implements DnsUpstreamStrategy{
  private final List<DnsUpstream> upstreams;

  public SequentialStrategy(List<DnsUpstream> upstreams) {
      this.upstreams = upstreams;
  }

  @Override
  public Future<DnsResponse> lookup(DnsQuery query) {
      Future<DnsResponse> f = Future.failedFuture("no upstream");
      for (DnsUpstream u : upstreams) {
          f = f.recover(err -> u.lookup(query).map(SafeDnsResponse::copy));
      }
      return f;
  }
}
