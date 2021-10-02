package io.crowds.dns;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DnsCache {

    public Map<String, DnsRecordGroup> map;

    public DnsCache() {
        this.map=new HashMap<>();
    }

    public void cache(String name, DnsRecord record){
        DnsRecordGroup group = map.computeIfAbsent(name, DnsRecordGroup::new);
        group.put(record);
//        System.out.println("cache "+map);


    }

    public List<DnsRecord> get(String name, DnsRecordType type){
//        System.out.println("get "+map);

        DnsRecordGroup group = map.computeIfAbsent(name, DnsRecordGroup::new);

        List<DnsRecord> records = group.get(type);
        return records;
    }


}
