package io.crowds.proxy.transport.proxy.trojan;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.TP;
import io.crowds.proxy.common.Socks;
import io.crowds.proxy.transport.Destination;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.*;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

public class TrojanCodec extends CombinedChannelDuplexHandler<ByteToMessageDecoder,MessageToByteEncoder<Object>> {

    private TP tp;

    public TrojanCodec(TP tp) {
        this.tp = tp;
        this.init(new Decoder(),new Encoder());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (TP.UDP==tp)
            super.channelRead(ctx, msg);
        else
            ctx.fireChannelRead(msg);
    }

    private class Encoder extends MessageToByteEncoder<Object>{

        private void encodePacket(DatagramPacket packet,ByteBuf out){
            InetSocketAddress recipient = packet.recipient();
            ByteBuf content = packet.content();
            Socks.encodeAddr(NetAddr.of(recipient),out);
            out.writeShort(content.readableBytes());
            out.writeByte('\r').writeByte('\n');
            out.writeBytes(content);
        }

        private void encodeRequest(TrojanRequest request,ByteBuf out){
            var password = request.getPassword();
            var destination = request.getDestination();
            var payload = request.getPayload();

            out.writeBytes(password);
            out.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
            out.writeByte(TP.TCP==destination.tp()?1:3);
            Socks.encodeAddr(destination.addr(),out);
            out.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
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
            return msg instanceof TrojanRequest||msg instanceof DatagramPacket;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
            if (msg instanceof TrojanRequest req){
                encodeRequest(req,out);
            }else if (msg instanceof DatagramPacket packet){
                encodePacket(packet,out);
            }
        }
    }


    /**
     * only on udp associate will use
     */
    private class Decoder extends ReplayingDecoder<Void>{

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            InetSocketAddress sender = Socks.decodeAddr(in);
            int length = in.readUnsignedShort();
            if (in.readByte()!='\r'||in.readByte()!='\n'){
                throw new IllegalStateException("except \r\n");
            }
            ByteBuf content=in.readBytes(length);
//            ByteBuf content=ctx.alloc().buffer().writeBytes(in.readSlice(length));
            out.add(new DatagramPacket(content,null,sender));

        }
    }



}
