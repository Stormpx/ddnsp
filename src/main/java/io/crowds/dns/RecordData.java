package io.crowds.dns;

import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecordData {

    private Map<DnsRecordType, List<DnsRecord>> typeMap=new HashMap<>();


    public void add(DnsRecord record){
        List<DnsRecord> dnsRecords = typeMap.computeIfAbsent(record.type(), k -> new ArrayList<>());

        dnsRecords.add(record);
    }

    public List<DnsRecord> get(DnsRecordType recordType){
        return typeMap.get(recordType);
    }


}
