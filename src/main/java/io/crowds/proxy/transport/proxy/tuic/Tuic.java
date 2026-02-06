package io.crowds.proxy.transport.proxy.tuic;

import io.crowds.proxy.DomainNetAddr;
import io.crowds.proxy.NetAddr;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class Tuic {

    public static int addrLength(NetAddr addr){
        int len = 1;
        if (addr==null){
            return len;
        }
        if (addr instanceof DomainNetAddr domain){
            String host = domain.getHost();
            if (host.length()>255){
                throw new RuntimeException("Hostname to long");
            }
            len+= 1 + host.length();
        }else{
            len+= addr.isIpv4()?4:16;
        }
        return len + 2;
    }

    public static void encodeAddr(NetAddr addr, ByteBuf out){
        if (addr==null){
            out.writeByte(0xff);
            return;
        }
        if (addr instanceof DomainNetAddr domain){
            String host = domain.getHost();
            if (host.length()>255){
                throw new RuntimeException("Hostname to long");
            }
            out.writeByte(0x00);
            out.writeByte(host.length());
            out.writeCharSequence(host, StandardCharsets.US_ASCII);
        }else{
            out.writeByte(addr.isIpv4()?0x01:0x02);
            out.writeBytes(addr.getByte());
        }
        out.writeShort(addr.getPort());
    }
    public static NetAddr decodeAddr(ByteBuf in){

        try {
            int type = in.readUnsignedByte();
            if (type==0xff){
                return null;
            }
            if (type==0x00){
                int len = in.readUnsignedByte();
                var host = in.readCharSequence(len,StandardCharsets.US_ASCII);
                int port = in.readUnsignedShort();
                return new DomainNetAddr(host.toString(),port);
            }
            int addrLen = 0;
            if (type==0x01){
                addrLen = 4;
            } else if (type==0x02){
                addrLen = 16;
            }else {
                throw new RuntimeException("Invaild addr type: "+type);
            }

            ByteBuf addr = in.readBytes(addrLen);
            int port = in.readUnsignedShort();
            return new NetAddr(new InetSocketAddress(InetAddress.getByAddress(ByteBufUtil.getBytes(addr)),port));

        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static boolean isUdpCommand(TuicCommand command){
        return command instanceof TuicCommand.Packet || command instanceof TuicCommand.Dissociate;
    }
    
    public static int getAssociateId(TuicCommand command) {
        return switch (command) {
            case TuicCommand.Packet packet -> packet.assocId();
            case TuicCommand.Dissociate dissociate -> dissociate.assocId();
            default -> 0;
        };
    }
}
