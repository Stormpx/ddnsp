package io.crowds.dns.cache;

import io.crowds.dns.DnsKit;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.dns.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class DnsCache {

    private EventLoop eventLoop;
    private Map<CacheKey, CacheEntries> cache;

    public DnsCache(EventLoop eventLoop) {
        this.cache =new ConcurrentHashMap<>();
        this.eventLoop = eventLoop;
    }



    private Stream<TtlRecord> cacheSection(DnsMessage message, DnsSection section){
        return IntStream.range(0, message.count(section)).mapToObj(i -> (DnsRecord)message.recordAt(section,i)).map(TtlRecord::new);
    }

    private void cache(CacheKey key,List<TtlRecord> value,EventLoop eventLoop){
        cache.compute(key,((key_, entries) -> {
            if (entries!=null)
                entries.cancel();
            CacheEntries cacheEntries;
            if (key.type()==DnsRecordType.CNAME){
                cacheEntries = new CnameEntries(value);
            }else{
                cacheEntries = new CacheEntries(value);
            }
            var loop =eventLoop==null?this.eventLoop:eventLoop;
            long maxTtl = cacheEntries.maxTtl();
            cacheEntries.withExpiration(
                    loop.schedule(()-> {
                        cache.computeIfPresent(key,(k,v)->v==cacheEntries?null:cacheEntries);
                    }, maxTtl, TimeUnit.SECONDS)
            );
            return cacheEntries;
        }));
    }

    public void cache(DnsRecord record,EventLoop eventLoop){
        CacheKey key = new CacheKey(record);
        cache(key,List.of(new TtlRecord(record)),eventLoop);
    }

    public void cacheMessage(DnsMessage message, EventLoop eventLoop){
        var ttlRecordGroups = Stream.of(cacheSection(message, DnsSection.ANSWER), cacheSection(message, DnsSection.AUTHORITY), cacheSection(message, DnsSection.ADDITIONAL))
                .flatMap(Function.identity()).collect(Collectors.groupingBy(TtlRecord::cacheKey));


        ttlRecordGroups.forEach((key, value) -> cache(key,value,eventLoop));
    }



    public void invalidate(CacheKey key){
        cache.computeIfPresent(key,(k,v)->{
            v.cancel();
            return null;
        });
    }

    public void invalidateAll(){
        cache.clear();
    }


    public boolean getAnswer(CacheKey key, boolean recursive,List<DnsRecord> results){
        CacheEntries entries = cache.get(key);
        long ts = System.currentTimeMillis();
        if (entries==null){
            CacheEntries cacheEntries = cache.get(new CacheKey(key.name(), DnsRecordType.CNAME));
            if (cacheEntries instanceof CnameEntries cnameEntries && !cacheEntries.isTimeout(ts)){
                String cname = cnameEntries.cname();
                TtlRecord ttlRecord = cacheEntries.records().get(0);
                results.add(DnsKit.clone(ttlRecord.record(),ttlRecord.remainTtl(ts)));
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
                .map(ttlRecord -> DnsKit.clone(ttlRecord.record(),ttlRecord.remainTtl(ts)))
                .forEach(results::add);
        return true;
    }

    public boolean getAnswer(String name,DnsRecordType type, boolean recursive,List<DnsRecord> results){
        return getAnswer(new CacheKey(name,type),recursive,results);
    }



    public List<DnsRecord> get(CacheKey key, boolean recursive){
        CacheEntries entries = cache.get(key);
        long ts = System.currentTimeMillis();
        if (entries==null){
            if (recursive){
                CacheEntries cacheEntries = cache.get(new CacheKey(key.name(), DnsRecordType.CNAME));
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
                .map(ttlRecord -> DnsKit.clone(ttlRecord.record(),ttlRecord.remainTtl(ts)))
                .toList();
    }


    public List<DnsRecord> get(String name,DnsRecordType type, boolean recursive) {
        return get(new CacheKey(name,type),recursive);
    }



}
