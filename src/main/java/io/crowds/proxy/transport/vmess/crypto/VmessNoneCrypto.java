package io.crowds.proxy.transport.vmess.crypto;

import io.netty.buffer.ByteBuf;

public class VmessNoneCrypto implements VmessCrypto {
    @Override
    public void encrypt(byte[] bytes, ByteBuf out) throws Exception {
        if (bytes.length!=0)
            out.writeBytes(bytes);
    }

    @Override
    public byte[] decrypt(byte[] bytes) throws Exception {
        return bytes;
    }

}
