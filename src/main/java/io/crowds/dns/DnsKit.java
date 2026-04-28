package io.crowds.dns;

import io.crowds.util.Inet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.dns.*;
import io.netty.util.CharsetUtil;

import java.net.InetAddress;
import java.util.stream.IntStream;
import java.util.stream.Stream;


public class DnsKit {

    public static ByteBuf encodeDomainName(String name, ByteBuf buf) {
        if (".".equals(name)) {
            // Root domain
            buf.writeByte(0);
            return buf;
        }

        final String[] labels = name.split("\\.");
        for (String label : labels) {
            final int labelLen = label.length();
            if (labelLen == 0) {
                // zero-length label means the end of the name.
                break;
            }

            buf.writeByte(labelLen);
            ByteBufUtil.writeAscii(buf, label);
        }

        buf.writeByte(0); // marks end of name field
        return buf;
    }

    public static String decodeDomainName(ByteBuf in) {
        return DefaultDnsRecordDecoder.decodeName(in);
    }


    public static DnsMessage msgCopy(DnsMessage source,DnsMessage dst,boolean copy){
        dst
                .setRecursionDesired(source.isRecursionDesired())
//                .setOpCode(source.opCode())
                .setZ(source.z());
        for (int i = 0; i < source.count(DnsSection.QUESTION); i++) {
            DnsRecord record = source.recordAt(DnsSection.QUESTION, i);
            dst.addRecord(DnsSection.QUESTION, copy?clone(record):record);
        }
        for (int i = 0; i < source.count(DnsSection.ANSWER); i++) {
            DnsRecord record = source.recordAt(DnsSection.ANSWER, i);
            dst.addRecord(DnsSection.ANSWER,  copy?clone(record):record);
        }
        for (int i = 0; i < source.count(DnsSection.AUTHORITY); i++) {
            DnsRecord record = source.recordAt(DnsSection.AUTHORITY, i);
            dst.addRecord(DnsSection.AUTHORITY, copy?clone(record):record);
        }
        for (int i = 0; i < source.count(DnsSection.ADDITIONAL); i++) {
            DnsRecord record = source.recordAt(DnsSection.ADDITIONAL, i);
            dst.addRecord(DnsSection.ADDITIONAL, copy?clone(record):record);
        }

        return dst;
    }

    public static DnsRecord clone(DnsRecord record,long ttl,boolean copyContent){
        if (record instanceof DnsQuestion) {
            return new DefaultDnsQuestion(record.name(),record.type(),record.dnsClass());
        } else if (record instanceof DnsRawRecord) {
            ByteBuf content = ((DnsRawRecord) record).content();
            content = !copyContent?content.slice():Unpooled.copiedBuffer(content);
            return new DefaultDnsRawRecord(record.name(),record.type(),record.dnsClass(),ttl,content);
        } else if (record instanceof DnsPtrRecord) {
            return new DefaultDnsPtrRecord(record.name(),record.dnsClass(),ttl,((DnsPtrRecord) record).hostname());
        } else if (record instanceof DnsOptEcsRecord) {
            return record;
        } else if (record instanceof DnsOptPseudoRecord) {
            return record;
        }
        return null;
    }

    public static DnsRecord clone(DnsRecord record,long ttl){
        return clone(record,ttl,true);
    }

    public static DnsRecord clone(DnsRecord record){
        return clone(record, record.timeToLive());
    }

    public static void encodeQueryHeader(DnsQuery query,int id,ByteBuf out){
        out.writeShort(id);
        int flags=0;
        flags |= (query.opCode().byteValue() & 0xFF) << 14;
        if (query.isRecursionDesired()) {
            flags |= 1 << 8;
        }
        out.writeShort(flags);
        out.writeShort(query.count(DnsSection.QUESTION));
        out.writeShort(0); // answerCount
        out.writeShort(0); // authorityResourceCount
        out.writeShort(query.count(DnsSection.ADDITIONAL));
    }


    public static Stream<InetAddress> getInetAddrFromResponse(DnsResponse response, boolean ipv4){
        if (DnsResponseCode.NOERROR != response.code()){
            return Stream.empty();
        }
        if (response.isTruncated()){
            return Stream.empty();
        }
        DnsRecordType type = ipv4 ? DnsRecordType.A : DnsRecordType.AAAA;
        return IntStream.range(0,response.count(DnsSection.ANSWER))
                .mapToObj(i-> (DnsRecord)response.recordAt(DnsSection.ANSWER,i))
                .filter(record-> record.type()==type)
                .filter(it->it instanceof DnsRawRecord)
                .map(it-> Inet.address(ByteBufUtil.getBytes(((DnsRawRecord) it).content())));


    }

}

