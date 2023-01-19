package io.crowds.dns;

import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsResponseCode;

public class SafeDnsResponse extends DefaultDnsResponse {
    public SafeDnsResponse(int id) {
        super(id);
    }

    public SafeDnsResponse(int id, DnsOpCode opCode) {
        super(id, opCode);
    }

    public SafeDnsResponse(int id, DnsOpCode opCode, DnsResponseCode code) {
        super(id, opCode, code);
    }
}
