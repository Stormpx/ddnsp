package io.crowds.proxy.common;

import io.crowds.proxy.NetAddr;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class Socks {


    public static InetSocketAddress decodeAddr(ByteBuf buf) {
        try {
            byte type = buf.readByte();
            if (type==1||type==4){
                byte[] ip=new byte[type==1?4:16];
                buf.readBytes(ip);
                InetAddress address = InetAddress.getByAddress(ip);
                return new InetSocketAddress(address,buf.readUnsignedShort());
            }else if (type==3){
                short len = buf.readUnsignedByte();
                byte[] domain=new byte[len];
                buf.readBytes(domain);
                return InetSocketAddress.createUnresolved(new String(domain, StandardCharsets.UTF_8),buf.readUnsignedShort());
            }else{
                throw new DecoderException("unsupported address type: " + (type & 255));
            }
        } catch (UnknownHostException | DecoderException e) {
            throw new RuntimeException(e.getMessage(),e);
        }
    }

    public static void encodeAddr(NetAddr addr,ByteBuf out){
        if (addr.isIpv4()){
            out.writeByte(0x01);
        }else if (addr.isIpv6()){
            out.writeByte(0x04);
        }else{
            out.writeByte(0x03);
            String host = addr.getHost();
            if (host.length()>256){
                throw new IllegalArgumentException("dest domain "+ host +" to long");
            }
            out.writeByte(host.length());
        }
        out.writeBytes(addr.getByte())
                .writeShort(addr.getPort());

    }

    public static class DecodeState{
        private byte type;

    }
}
