package io.crowds.dns;

import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;

import java.util.*;

public class DnsRecordGroup {

    private String name;
    private Map<DnsRecordType, List<RecordCache>> map;

    public DnsRecordGroup(String name) {
        this.name=name;
        this.map=new HashMap<>();
    }

    public void put(DnsRecord record){
        map.computeIfAbsent(record.type(),k->new ArrayList<>()).add(new RecordCache(DnsKit.clone(record)));
    }


    public List<DnsRecord> get(DnsRecordType type){
        List<RecordCache> recordCaches = map.get(type);
        if (recordCaches==null)
            return Collections.emptyList();
        List<DnsRecord> result = new ArrayList<>();
        for (Iterator<RecordCache> iterator = recordCaches.iterator(); iterator.hasNext(); ) {
            RecordCache cache = iterator.next();
            if (cache.isTimeOut()) {
                iterator.remove();
                continue;
            }
            result.add(DnsKit.clone(cache.record,cache.remainTTL()));
        }
        return result;
    }

    @Override
    public String toString() {
        return "DnsRecordGroup{" + "name='" + name + '\'' + ", map=" + map + '}';
    }

    class RecordCache{
        private DnsRecord record;
        private long timestamp;

        public RecordCache(DnsRecord record) {
            this.record = record;
            this.timestamp=System.currentTimeMillis()/1000;
        }

        public boolean isTimeOut(){
            return (System.currentTimeMillis()/1000)-this.timestamp>=record.timeToLive();
        }
        public long remainTTL(){
            return record.timeToLive()-((System.currentTimeMillis()/1000)-this.timestamp);
        }

    }


}
