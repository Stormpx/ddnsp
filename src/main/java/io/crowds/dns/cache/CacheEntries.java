package io.crowds.dns.cache;


import io.crowds.dns.DnsKit;
import io.netty.handler.codec.dns.DnsRecord;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;

public class CacheEntries {

    private ScheduledFuture<?> expirationFuture;


    private List<TtlRecord> records;


    public CacheEntries(List<TtlRecord> records) {
        this.records = records;
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
        return records.stream().map(TtlRecord::record).map(DnsRecord::timeToLive).max(Comparator.naturalOrder()).orElse(-1L);
    }

    public long discardTime() {
        return records.stream().max(Comparator.comparingLong(TtlRecord::expireTimestamp)).map(TtlRecord::expireTimestamp).orElse(-1L);
    }

    public boolean isTimeout(long timestamp){
        return discardTime()<timestamp;
    }



}
