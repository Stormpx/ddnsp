package io.crowds.compoments.capsule;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class CapsuleEncoder extends MessageToByteEncoder<Capsule> {

    private void encodeVarInt(long val,ByteBuf out){
        if (val < 63){
            out.writeByte((int) val);
        }else if (val < 16383){
            out.writeShort((int) (0x4000 + val));
        }else if (val < 1073741823L){
            out.writeInt((int) (0x80000000L + val));
        }else if (val < 4611686018427387903L){
            out.writeLong( 0xc000000000000000L + val);
        } else {
            throw new IllegalArgumentException("Variable-Length Integer too large");
        }
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Capsule msg, ByteBuf out) throws Exception {

        int type = msg.type();
        ByteBuf content = msg.content();
        encodeVarInt(type,out);
        encodeVarInt(content.readableBytes(),out);
        out.writeBytes(content);

    }
}
