package io.crowds.dns;

import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.util.ReferenceCountUtil;

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

    public static DnsResponse copy(DnsResponse response){
        if (response instanceof SafeDnsResponse){
          return response;
        }
        DefaultDnsResponse dnsResponse = new SafeDnsResponse(response.id(), response.opCode(), response.code());
        DnsKit.msgCopy(response,dnsResponse,true);
        ReferenceCountUtil.safeRelease(response);
        return dnsResponse;
    }
}
