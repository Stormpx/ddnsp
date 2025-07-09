package io.crowds.dns.server;

import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.ReferenceCounted;

import java.net.InetSocketAddress;

public interface DnsRequest extends ReferenceCounted {

    InetSocketAddress sender();

    default int id(){
        return query().id();
    }

    DnsQuery query();

    DnsResponse newResponse();

    void response(DnsResponse response);

    @Override
    default int refCnt() {
        return query().refCnt();
    }

    @Override
    default ReferenceCounted retain() {
        return query().retain();
    }

    @Override
    default ReferenceCounted retain(int i) {
        return query().retain(i);
    }

    @Override
    default ReferenceCounted touch() {
        return query().touch();
    }

    @Override
    default ReferenceCounted touch(Object o) {
        return query().touch(o);
    }

    @Override
    default boolean release() {
        return query().release();
    }

    @Override
    default boolean release(int i) {
        return query().release(i);
    }
}
