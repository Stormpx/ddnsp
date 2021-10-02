package io.crowds.dns.packet;

import io.netty.handler.codec.dns.DnsRecordType;

public class DnsQuestion {

    private String name;
    private DnsRecordType type;
    private int qClass;

    public String getName() {
        return name;
    }

    public DnsQuestion setName(String name) {
        this.name = name;
        return this;
    }

    public DnsRecordType getType() {
        return type;
    }

    public DnsQuestion setType(DnsRecordType type) {
        this.type = type;
        return this;
    }

    public int getqClass() {
        return qClass;
    }

    public DnsQuestion setqClass(int qClass) {
        this.qClass = qClass;
        return this;
    }
}
