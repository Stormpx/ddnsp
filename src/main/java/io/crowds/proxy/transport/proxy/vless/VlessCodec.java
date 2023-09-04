package io.crowds.proxy.transport.proxy.vless;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.Destination;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class VlessCodec extends CombinedChannelDuplexHandler<ByteToMessageDecoder, MessageToByteEncoder<Object>> {
    private final static Logger logger= LoggerFactory.getLogger(VlessCodec.class);

    private Destination defaultDestination;

    public VlessCodec(Destination defaultDestination) {
        this.defaultDestination=defaultDestination;
        this.init(new Decoder(),new Encoder());
    }

    private class Encoder extends MessageToByteEncoder<Object>{

        private void encodePacket(DatagramPacket packet, ByteBuf out){
            out.writeShort(packet.content().readableBytes());
            out.writeBytes(packet.content());
        }

        private void encodeRequest(VlessRequest request, ByteBuf out){
            var id = request.getId();
            var dest = request.getDestination();
            var payload = request.getPayload();

            out.writeByte(0);
            out.writeLong(id.getMostSignificantBits()).writeLong(id.getLeastSignificantBits());
            out.writeByte(0);
            out.writeByte(dest.tp()== TP.TCP?1:2);
            NetAddr addr = dest.addr();
            byte[] addrBytes = addr.getByte();
            out.writeShort(addr.getPort());
            if (addr.isIpv4()||addr.isIpv6()){
                out.writeByte(addr.isIpv4()?1:3);
            }else{
                out.writeByte(2).writeByte(addrBytes.length);
            }
            out.writeBytes(addrBytes);

            if (payload !=null){
                if (payload instanceof ByteBuf buf){
                    out.writeBytes(buf);
                }else if (payload instanceof DatagramPacket packet){
                    encodePacket(packet,out);
                }
                ReferenceCountUtil.safeRelease(payload);
            }
        }


        @Override
        public boolean acceptOutboundMessage(Object msg) throws Exception {
            return msg instanceof VlessRequest||msg instanceof DatagramPacket;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
            if (msg instanceof VlessRequest req){
                encodeRequest(req,out);
            }else if (msg instanceof DatagramPacket packet){
                encodePacket(packet,out);
            }
        }
    }


    /**
     *
     */
    private class Decoder extends ByteToMessageDecoder {
        private byte state;
        private short addonsLen=-1;
        private int packetLen=-1;
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            if (state==0) {
                if (addonsLen==-1){
                    if (in.readableBytes()<2){
                        return;
                    }
                    byte ver = in.readByte();
                    if (ver != 0) {
                        logger.warn("unexpected response version {}. close the channel", ver);
                        ctx.channel().close();
                        return;
                    }
                    addonsLen=in.readByte();
                }
                if (in.readableBytes()<addonsLen){
                    return;
                }
                in.skipBytes(addonsLen);
                state=1;
            }

            if (state==1){
                if (defaultDestination.tp()==TP.TCP){
                    out.add(in.readRetainedSlice(in.readableBytes()));
                }else{
                    if (packetLen==-1){
                        packetLen = in.readUnsignedShort();
                    }
                    if (packetLen!=-1){
                        ByteBuf buf = in.readRetainedSlice(packetLen);
                        out.add(new DatagramPacket(buf,null, defaultDestination.addr().getAsInetAddr()));
                        packetLen=-1;
                    }
                }
            }


        }
    }



}