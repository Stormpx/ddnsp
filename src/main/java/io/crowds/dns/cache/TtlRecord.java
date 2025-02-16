package io.crowds.dns.cache;

import io.crowds.dns.DnsKit;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;

import java.net.InetAddress;
import java.net.UnknownHostException;


public class TtlRecord{
    private final DnsRecord record;
    private final long expireTimestamp;

    TtlRecord(DnsRecord record) {
        this.record = DnsKit.clone(record);
        this.expireTimestamp = System.currentTimeMillis()+(record.timeToLive()*1000);
    }

    public static TtlRecord of(DnsRecord record){
        if (record.type()== DnsRecordType.A||record.type()==DnsRecordType.AAAA){
            return new AddrTtlRecord((DnsRawRecord) record);
        }
        return new TtlRecord(record);
    }

    public DnsRecord record() {
        return record;
    }

    public long expireTimestamp() {
        return expireTimestamp;
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


    public static class AddrTtlRecord extends TtlRecord{
        private final InetAddress address;

        AddrTtlRecord(DnsRawRecord record) {
            super(record);
            try {
                this.address=InetAddress.getByAddress(ByteBufUtil.getBytes(record.content()));
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        }

        public InetAddress address() {
            return address;
        }
    }
}
