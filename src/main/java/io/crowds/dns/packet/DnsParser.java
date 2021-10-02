package io.crowds.dns.packet;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.vertx.core.buffer.Buffer;

public class DnsParser {


    public void parse(Buffer buffer){
        ByteBuf byteBuf = buffer.getByteBuf();
//        DnsPacketHeader header = new DnsPacketHeader();

        short id = byteBuf.readShort();

        byte b = byteBuf.readByte();
        boolean qr= (b&0b10000000) == 0b10000000;
        byte opCode= (byte) ((b>>3)&0b1111);
        boolean aa= (b& 0b100) ==0b100;
        boolean tc= (b& 0b10) == 0b10;
        boolean rd= (b& 0b1) == 0b1;

        byte rb = byteBuf.readByte();
        boolean ra= (rb&0b10000000) == 0b10000000;
        byte z= (byte) ((rb >> 4)&0b111);
        byte rCode=(byte)(rb&0b1111);
        int qdCount=byteBuf.readUnsignedShort();
        int anCount=byteBuf.readUnsignedShort();
        int nsCount=byteBuf.readUnsignedShort();
        int arCount=byteBuf.readUnsignedShort();

//        header.setId(id)
//                .setQr(qr)
//                .setOpCode(DnsOpCode.valueOf(opCode))
//                .setAa(aa)
//                .setTc(tc)
//                .setRd(rd)
//                .setRa(ra)
//                .setZ(z)
//                .setrCode(DnsResponseCode.valueOf(rCode))
//                .setQdCount(qdCount)
//                .setAnCount(anCount)
//                .setNsCount(nsCount)
//                .setArCount(arCount);



    }

    public String parseDomainName(ByteBuf buf){

        return "";

    }

}
