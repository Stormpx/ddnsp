package io.crowds.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import javax.crypto.Cipher;

public class ByteBufCipher {



    public static void doFinal(Cipher cipher, ByteBuf in, ByteBuf out) throws Exception {
        int outputSize = cipher.getOutputSize(in.readableBytes());
        out.ensureWritable(outputSize);
        int writerIndex = out.writerIndex();
        cipher.doFinal(in.nioBuffer(),out.nioBuffer(writerIndex,outputSize));
        in.skipBytes(in.readableBytes());
        out.writerIndex(writerIndex+outputSize);
    }

    public static ByteBuf doFinal(Cipher cipher, ByteBuf in) throws Exception {
        return doFinal(cipher,in,in.alloc());
    }

    public static ByteBuf doFinal(Cipher cipher, ByteBuf in, ByteBufAllocator allocator) throws Exception {
        int outputSize = cipher.getOutputSize(in.readableBytes());
        ByteBuf out = allocator.buffer(outputSize);
        cipher.doFinal(in.nioBuffer(),out.nioBuffer(0,outputSize));
        in.skipBytes(in.readableBytes());
        out.writerIndex(outputSize);
        return out;
    }

}
