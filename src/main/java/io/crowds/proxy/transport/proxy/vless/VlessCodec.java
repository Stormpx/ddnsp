package io.crowds.proxy.transport.proxy.vless;

import com.google.protobuf.InvalidProtocolBufferException;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.Destination;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class VlessCodec extends CombinedChannelDuplexHandler<ByteToMessageDecoder, MessageToByteEncoder<Object>> {
    private final static Logger logger= LoggerFactory.getLogger(VlessCodec.class);

    private UUID uuid;
    private Destination dest;
    private Vless.Flow flow = Vless.Flow.NONE;

    public VlessCodec(Destination dest) {
        this.dest = dest;
        this.init(new Decoder(),new Encoder());
    }

    private class Encoder extends MessageToByteEncoder<Object>{

        private boolean uuidWritten=false;
        private boolean noMoreVisionPadding=false;

        private void encodePacket(DatagramPacket packet, ByteBuf out){
            out.writeShort(packet.content().readableBytes());
            out.writeBytes(packet.content());
        }

        private void encodeVisionData(VisionData data,ByteBuf out){
            if (uuid==null){
                throw new IllegalStateException("Should send VlessRequest with UUID before VisionData");
            }
            ByteBuf msg = data.data();
            if (!data.padding()){
                out.writeBytes(msg);
                return;
            }

            if (noMoreVisionPadding){
                throw new IllegalStateException("No more vision padding allowed after COMMAND_PADDING_END or COMMAND_PADDING_DIRECT");
            }

            ByteBuf content = msg;
            if (msg.readableBytes()>8192){
                content = msg.readSlice(8192);
            }
            int contentLen = content.readableBytes();
            int paddingLen = 0;

            if (contentLen<900&data.longPadding()){
                int len = ThreadLocalRandom.current().nextInt(0,500);
                paddingLen = len + 900 - contentLen;
            }else{
                paddingLen = ThreadLocalRandom.current().nextInt(0,256);
            }

            if (!uuidWritten){
                assert uuid!=null;
                out.writeLong(uuid.getMostSignificantBits()).writeLong(uuid.getLeastSignificantBits());
                uuidWritten=true;
            }

            byte paddingCommand = data.command();
            out.writeByte(paddingCommand);
            out.writeShort(contentLen);
            out.writeShort(paddingLen);
            out.writeBytes(content);
            out.writeZero(paddingLen);

            if (paddingCommand == Vless.COMMAND_PADDING_END||paddingCommand==Vless.COMMAND_PADDING_DIRECT){
                noMoreVisionPadding = true;
            }

            if (msg.isReadable()){
                if (noMoreVisionPadding){
                    data = new VisionData(paddingCommand,data.data(),false,false);
                }
                encodeVisionData(data, out);
            }
        }

        private void encodeRequest(VlessRequest request, ByteBuf out){
            var id = request.getId();
            var dest = request.getDestination();
            Objects.requireNonNull(id);
            Objects.requireNonNull(dest);
            var addons = request.getAddons();

            out.writeByte(0);
            out.writeLong(id.getMostSignificantBits()).writeLong(id.getLeastSignificantBits());
            if (addons==null){
                out.writeByte(0);
            }else{
                byte[] bytes = addons.toByteArray();
                out.writeByte(bytes.length);
                out.writeBytes(bytes);
            }

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

            uuid = id;
            flow = request.getFlow();

        }


        @Override
        public boolean acceptOutboundMessage(Object msg) throws Exception {
            return msg instanceof VlessRequest||msg instanceof VisionData||msg instanceof DatagramPacket;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
            if (msg instanceof VlessRequest req){
                encodeRequest(req,out);
            }else if (msg instanceof VisionData data){
                encodeVisionData(data,out);
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

        //flow: none
        private int packetLen=-1;

        //flow: xrv
        private boolean enableXRV = false;
        private boolean paddingBuffer = true;
        private boolean uuidRead = false;
        private byte paddingCommand = -1;
        private int contentLen = -1;
        private int paddingLen = -1;

        private void decodeResponseHeader(ChannelHandlerContext ctx, ByteBuf in){
            if (addonsLen==-1){
                if (in.readableBytes()<2){
                    return;
                }
                byte ver = in.readByte();
                if (ver != 0) {
                    logger.warn("Unexpected response version {}. close the channel", ver);
                    ctx.channel().close();
                    return;
                }
                addonsLen=in.readByte();
            }
            if (in.readableBytes()<addonsLen){
                return;
            }
            in.skipBytes(addonsLen);
            if (flow == Vless.Flow.XRV) {
                enableXRV = true;
            }
            state = 1;
        }

        private void decodeNoneFlow(ChannelHandlerContext ctx, ByteBuf in, List<Object> out){
            if (dest.tp()==TP.TCP){
                out.add(in.readRetainedSlice(in.readableBytes()));
            }else{
                if (packetLen==-1){
                    packetLen = in.readUnsignedShort();
                }
                if (packetLen!=-1){
                    ByteBuf buf = in.readRetainedSlice(packetLen);
                    out.add(new DatagramPacket(buf,null, dest.addr().getAsInetAddr()));
                    packetLen=-1;
                }
            }
        }

        private void decodeVisionFlow(ChannelHandlerContext ctx, ByteBuf in, List<Object> out){
            if (!paddingBuffer){
                out.add(in.readRetainedSlice(in.readableBytes()));
            }
            if (!uuidRead){
                if (in.readableBytes()<16){
                    return;
                }
                long mostSignificantBits = in.readLong();
                long leastSignificantBits = in.readLong();
                if (!(uuid.getMostSignificantBits()==mostSignificantBits &&uuid.getLeastSignificantBits()==leastSignificantBits)){
                    throw new IllegalStateException("Invalid vision uuid");
                }
                uuidRead=true;
            }
            if (paddingCommand == -1){
                if (in.readableBytes()<6){
                    return;
                }
                paddingCommand = in.readByte();
                contentLen = in.readShort();
                paddingLen = in.readShort();
            }

            if (this.contentLen>0){
                int len = Math.min(in.readableBytes(),this.contentLen);
                if (len<=0){
                    return;
                }
                ByteBuf content = in.readRetainedSlice(len);
                out.add(new VisionData(paddingCommand,content,false,false));
                this.contentLen -= len;
            }

            int skipBytes = Math.min(in.readableBytes(),paddingLen);
            if (skipBytes <= 0) {
                return;
            }
            in.skipBytes(skipBytes);
            paddingLen -= skipBytes;
            if (paddingLen <= 0) {
                if (paddingCommand == Vless.COMMAND_PADDING_END || paddingCommand == Vless.COMMAND_PADDING_DIRECT) {
                    paddingBuffer = false;
                }
                paddingCommand = -1;
                contentLen = -1;
                paddingLen = -1;
            }

        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            if (state==0) {
                decodeResponseHeader(ctx, in);
            }

            //decode payload
            if (state==1){
                if (!enableXRV){
                    decodeNoneFlow(ctx, in, out);
                }else{
                    decodeVisionFlow(ctx, in, out);
                }
            }


        }
    }



}