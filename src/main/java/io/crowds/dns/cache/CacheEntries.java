package io.crowds.dns.cache;


import io.netty.handler.codec.dns.DnsRecord;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

public class CacheEntries {

    private ScheduledFuture<?> expirationFuture;


    private final List<TtlRecord> records;

    private final long maxTimeToLive;

    private final long expirationTime;

    public CacheEntries(List<TtlRecord> records) {
        this.records = records;
        this.maxTimeToLive = records.stream().map(TtlRecord::record).map(DnsRecord::timeToLive).max(Comparator.comparingLong(Long::longValue)).orElse(-1L);
        this.expirationTime = records.stream().map(TtlRecord::expireTimestamp).max(Comparator.comparingLong(Long::longValue)).orElse(-1L);
    }

    public CacheEntries withExpiration(ScheduledFuture<?> expirationFuture) {
        this.expirationFuture = expirationFuture;
        return this;
    }

    public void cancel(){
        if (expirationFuture!=null)
            expirationFuture.cancel(false);
    }



    public List<TtlRecord> records() {
        return records;
    }

    public long maxTtl(){
        return maxTimeToLive;
    }

    public long expirationTime() {
        return expirationTime;
    }

    public boolean isTimeout(long timestamp){
        return expirationTime()<timestamp;
    }



}
