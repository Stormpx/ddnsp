package io.crowds.dns.cache;

import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;

public record CacheKey(String name, DnsRecordType type) {

    public CacheKey(DnsRecord record) {
        this(record.name(),record.type());
    }
}
