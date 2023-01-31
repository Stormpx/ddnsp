package io.crowds.dns;

import io.crowds.util.Inet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.dns.*;
import io.netty.util.CharsetUtil;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;


public class DnsKit {

    public static DnsClient DNS_CLIENT=null;

    public static DnsClient client(){
        return DNS_CLIENT;
    }


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
        int position = -1;
        int checked = 0;
        final int end = in.writerIndex();
        final int readable = in.readableBytes();

        // Looking at the spec we should always have at least enough readable bytes to read a byte here but it seems
        // some servers do not respect this for empty names. So just workaround this and return an empty name in this
        // case.
        //
        // See:
        // - https://github.com/netty/netty/issues/5014
        // - https://www.ietf.org/rfc/rfc1035.txt , Section 3.1
        if (readable == 0) {
            return ".";
        }

        final StringBuilder name = new StringBuilder(readable << 1);
        while (in.isReadable()) {
            final int len = in.readUnsignedByte();
            final boolean pointer = (len & 0xc0) == 0xc0;
            if (pointer) {
                if (position == -1) {
                    position = in.readerIndex() + 1;
                }

                if (!in.isReadable()) {
                    throw new CorruptedFrameException("truncated pointer in a name");
                }

                final int next = (len & 0x3f) << 8 | in.readUnsignedByte();
                if (next >= end) {
                    throw new CorruptedFrameException("name has an out-of-range pointer");
                }
                in.readerIndex(next);

                // check for loops
                checked += 2;
                if (checked >= end) {
                    throw new CorruptedFrameException("name contains a loop.");
                }
            } else if (len != 0) {
                if (!in.isReadable(len)) {
                    throw new CorruptedFrameException("truncated label in a name");
                }
                name.append(in.toString(in.readerIndex(), len, CharsetUtil.UTF_8)).append('.');
                in.skipBytes(len);
            } else { // len == 0
                break;
            }
        }

        if (position != -1) {
            in.readerIndex(position);
        }

        if (name.length() == 0) {
            return ".";
        }

        if (name.charAt(name.length() - 1) != '.') {
            name.append('.');
        }

        return name.toString();
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

    public static DnsRecord clone(DnsRecord record){
        if (record instanceof DnsQuestion) {
            return record;
        } else if (record instanceof DnsPtrRecord) {
//            return new DefaultDnsPtrRecord(record.name(),record.dnsClass(),record.timeToLive(),((DnsPtrRecord) record).hostname());
            String hostname = ((DnsPtrRecord) record).hostname();
            ByteBuf content = encodeDomainName(hostname, Unpooled.buffer());
            return new DefaultDnsRawRecord(record.name(),DnsRecordType.PTR,record.dnsClass(),record.timeToLive(),content);
        } else if (record instanceof DnsOptEcsRecord) {
            return record;
        } else if (record instanceof DnsOptPseudoRecord) {
            return record;
        } else if (record instanceof DnsRawRecord) {
            return new DefaultDnsRawRecord(record.name(),record.type(),record.dnsClass(),record.timeToLive(),Unpooled.copiedBuffer(((DnsRawRecord) record).content()));
        }
        return null;
    }

    public static DnsRecord clone(DnsRecord record,long ttl){
        if (record instanceof DnsQuestion) {
            return new DefaultDnsQuestion(record.name(),record.type(),record.dnsClass());
        } else if (record instanceof DnsPtrRecord) {
            String hostname = ((DnsPtrRecord) record).hostname();
            ByteBuf content = encodeDomainName(hostname, Unpooled.buffer());
            return new DefaultDnsRawRecord(record.name(),DnsRecordType.PTR,record.dnsClass(),ttl,content);
//            return new DefaultDnsPtrRecord(record.name(),record.dnsClass(),ttl,((DnsPtrRecord) record).hostname());

        } else if (record instanceof DnsOptEcsRecord) {
            return record;
        } else if (record instanceof DnsOptPseudoRecord) {
            return record;
        } else if (record instanceof DnsRawRecord) {
            return new DefaultDnsRawRecord(record.name(),record.type(),record.dnsClass(),ttl,Unpooled.copiedBuffer(((DnsRawRecord) record).content()));
        }
        return null;
    }

    public static void encodeQueryHeader(DnsQuery query,ByteBuf out){
        out.writeShort(query.id());
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

