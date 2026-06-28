package io.crowds.dns.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.crowds.dns.DnsKit;
import io.netty.handler.codec.dns.*;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class DnsCache {

    private final Cache<CacheKey, CacheEntries> cache;

    public DnsCache() {
        this.cache = Caffeine.newBuilder()
                .expireAfter(new Expiry<CacheKey, CacheEntries>() {
                    @Override
                    public long expireAfterCreate(CacheKey key, CacheEntries entries, long currentTime) {
                        return TimeUnit.SECONDS.toNanos(entries.maxTtl());
                    }

                    @Override
                    public long expireAfterUpdate(CacheKey key, CacheEntries entries, long currentTime, long currentDuration) {
                        return TimeUnit.SECONDS.toNanos(entries.maxTtl());
                    }

                    @Override
                    public long expireAfterRead(CacheKey key, CacheEntries entries, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }


    private Stream<TtlRecord> cacheSection(DnsMessage message, DnsSection section){
        return IntStream.range(0, message.count(section)).mapToObj(i -> (DnsRecord)message.recordAt(section,i)).map(TtlRecord::of);
    }

    private void cache(CacheKey key, List<TtlRecord> value){
        CacheEntries cacheEntries;
        if (key.type()==DnsRecordType.CNAME){
            cacheEntries = new CnameEntries(value);
        }else{
            cacheEntries = new CacheEntries(value);
        }
        cache.put(key, cacheEntries);
    }

    public void cache(DnsRecord record){
        CacheKey key = new CacheKey(record);
        cache(key, List.of(new TtlRecord(record)));
    }

    public void cacheMessage(DnsMessage message){
        var ttlRecordGroups = Stream.of(cacheSection(message, DnsSection.ANSWER), cacheSection(message, DnsSection.AUTHORITY), cacheSection(message, DnsSection.ADDITIONAL))
                .flatMap(Function.identity()).collect(Collectors.groupingBy(TtlRecord::cacheKey));

        ttlRecordGroups.forEach((key, value) -> cache(key, value));
    }



    public void invalidate(CacheKey key){
        cache.invalidate(key);
    }

    public void invalidateAll(){
        cache.invalidateAll();
    }


    public boolean getAnswer(CacheKey key, boolean recursive, List<DnsRecord> results){
        CacheEntries entries = cache.getIfPresent(key);
        long ts = System.currentTimeMillis();
        if (entries==null){
            CacheEntries cacheEntries = cache.getIfPresent(new CacheKey(key.name(), DnsRecordType.CNAME));
            if (cacheEntries instanceof CnameEntries cnameEntries && !cacheEntries.isTimeout(ts)){
                String cname = cnameEntries.cname();
                TtlRecord ttlRecord = cacheEntries.records().getFirst();
                results.add(DnsKit.clone(ttlRecord.record(),ttlRecord.remainTimeToLive(ts),false));
                if (recursive){
                    return getAnswer(new CacheKey(cname,key.type()),true,results);
                }
            }
            return false;
        }
        if (entries.isTimeout(ts)){
            return false;
        }
        entries.records().stream()
                .filter(ttlRecord -> !ttlRecord.isTimeout(ts))
                .map(ttlRecord -> DnsKit.clone(ttlRecord.record(),ttlRecord.remainTimeToLive(ts),false))
                .forEach(results::add);
        return true;
    }

    public boolean getAnswer(String name,DnsRecordType type, boolean recursive,List<DnsRecord> results){
        return getAnswer(new CacheKey(name,type),recursive,results);
    }



    public List<DnsRecord> get(CacheKey key, boolean recursive){
        CacheEntries entries = cache.getIfPresent(key);
        long ts = System.currentTimeMillis();
        if (entries==null){
            if (recursive){
                CacheEntries cacheEntries = cache.getIfPresent(new CacheKey(key.name(), DnsRecordType.CNAME));
                if (cacheEntries instanceof CnameEntries cnameEntries && !cacheEntries.isTimeout(ts)){
                    return get(new CacheKey(cnameEntries.cname(), key.type()),true);
                }
            }
            return List.of();
        }
        if (entries.isTimeout(ts)){
            return List.of();
        }
        return entries.records().stream()
                .filter(ttlRecord -> !ttlRecord.isTimeout(ts))
                .map(ttlRecord -> DnsKit.clone(ttlRecord.record(),ttlRecord.remainTimeToLive(ts),false))
                .toList();
    }


    public List<DnsRecord> get(String name,DnsRecordType type, boolean recursive) {
        return get(new CacheKey(name,type),recursive);
    }


    public List<InetAddress> lightWeightGet(CacheKey key, boolean recursive){
        if (key.type()!=DnsRecordType.A&&key.type()!=DnsRecordType.AAAA){
            throw new UnsupportedOperationException("light-weight get cache only support A or AAAA type");
        }
        CacheEntries entries = cache.getIfPresent(key);
        long ts = System.currentTimeMillis();
        if (entries==null){
            if (recursive){
                CacheEntries cacheEntries = cache.getIfPresent(new CacheKey(key.name(), DnsRecordType.CNAME));
                if (cacheEntries instanceof CnameEntries cnameEntries && !cacheEntries.isTimeout(ts)){
                    return lightWeightGet(new CacheKey(cnameEntries.cname(), key.type()),true);
                }
            }
            return List.of();
        }
        if (entries.isTimeout(ts)){
            return List.of();
        }
        return entries.records().stream()
                      .filter(it->it instanceof TtlRecord.AddrTtlRecord)
                      .filter(ttlRecord -> !ttlRecord.isTimeout(ts))
                      .map(it->(TtlRecord.AddrTtlRecord) it)
                      .map(TtlRecord.AddrTtlRecord::address)
                      .toList();
    }

}
