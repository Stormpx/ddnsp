package io.crowds.compoments.capsule;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.TooLongFrameException;

import java.util.List;

public class CapsuleDecoder extends ReplayingDecoder<CapsuleDecoder.State> {

    private final long maximumLength;

    private long type;
    private long length;
    private boolean discard;

    public enum State {
        TYPE,
        LENGTH,
        DATA;
    }

    public CapsuleDecoder(long maximumLength) {
        super(State.TYPE);
        this.maximumLength = maximumLength;
    }

    private long decodeVarInt(ByteBuf in){

        int v = in.readUnsignedByte();
        int prefix = v >> 6;
        int length = 1 << prefix;
        long r = (v & 0x3f);
        for (int i = 0; i < length-1; i++) {
            r = (r << 8) + in.readUnsignedByte();
        }

        return r;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {

        switch (state()) {
            case TYPE -> {
                type = decodeVarInt(in);
                checkpoint(State.LENGTH);
            }
            case LENGTH -> {
                length = decodeVarInt(in);
                System.out.println(length);
                if (length > Integer.MAX_VALUE) {
                    throw new TooLongFrameException(String.valueOf(length));
                }
                if (length == 0) {
                    out.add(new Capsule((int) type, Unpooled.EMPTY_BUFFER));
                    checkpoint(State.DATA);
                    return;
                }
                discard = length > maximumLength;
                checkpoint(State.DATA);
            }
            case DATA -> {
                if (length > 0) {
                    if (discard) {
                        long len = Math.min(length, in.readableBytes());
                        in.skipBytes((int) len);
                        this.length -= len;
                    } else {
                        ByteBuf data = in.readRetainedSlice((int) length);
                        out.add(new Capsule((int) type, data));
                    }
                }
                checkpoint(State.TYPE);
            }
            default -> throw new IllegalStateException("Unexpected value: " + state());
        }

    }
}
