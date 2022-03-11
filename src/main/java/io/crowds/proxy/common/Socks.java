package io.crowds.proxy.common;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class Socks {


    public static InetSocketAddress readAddr(ByteBuf buf) {
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

}
