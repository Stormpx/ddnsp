package io.crowds.proxy.transport.vmess.crypto;

import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;

public class VmessNoneCrypto implements VmessCrypto {
    @Override
    public void encrypt(byte[] bytes, ByteBuf out) throws Exception {
        if (bytes.length!=0)
            out.writeBytes(bytes);
    }

    @Override
    public void encrypt(ByteBuf bytes, ByteBuf out) throws Exception {
        out.writeBytes(bytes);
    }


    @Override
    public byte[] decrypt(byte[] bytes) throws Exception {
        return bytes;
    }

    @Override
    public ByteBuf decrypt(ByteBuf bytes) throws Exception {
        return bytes.alloc().buffer().writeBytes(bytes);
    }

}
