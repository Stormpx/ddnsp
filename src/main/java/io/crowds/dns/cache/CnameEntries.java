package io.crowds.dns.cache;

import io.crowds.dns.DnsKit;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.resolver.dns.DefaultDnsCnameCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CnameEntries extends CacheEntries{

    private String cname;

    public CnameEntries(List<TtlRecord> records) {
        super(List.of(records.get(0)));

        TtlRecord record = records.get(0);
        if (!(record.record() instanceof DnsRawRecord rawRecord)){
            throw new IllegalArgumentException("illegal dns cname record.");
        }
        int readerIndex = rawRecord.content().readerIndex();
        String cname = DnsKit.decodeDomainName(rawRecord.content());
        rawRecord.content().readerIndex(readerIndex);
        this.cname=cname;

    }

    public String cname(){
        return cname;
    }
}
