package io.crowds.dns.cache;

import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;

import java.util.Objects;

public record CacheKey(String name, DnsRecordType type) {

    public CacheKey {
        Objects.requireNonNull(name);
        if (!name.endsWith(".")) {
            name = name + ".";
        }
    }

    public CacheKey(DnsRecord record) {
        this(record.name(),record.type());
    }
}
