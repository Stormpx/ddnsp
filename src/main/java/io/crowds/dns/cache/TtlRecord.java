package io.crowds.dns.cache;

import io.netty.handler.codec.dns.DnsRecord;

public record TtlRecord(DnsRecord record, long expireTimestamp){
    TtlRecord(DnsRecord record) {
        this(record,System.currentTimeMillis()+(record.timeToLive()*1000));
    }

    public CacheKey cacheKey(){
        return new CacheKey(record);
    }
    public boolean isTimeout(long timestamp){
        return expireTimestamp<timestamp;
    }
    public int remainTimeToLive(long timestamp){
        return Math.toIntExact((expireTimestamp - timestamp)/1000);
    }
}
