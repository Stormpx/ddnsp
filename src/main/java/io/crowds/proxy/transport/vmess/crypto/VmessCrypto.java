package io.crowds.proxy.transport.vmess.crypto;

import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;


public interface VmessCrypto {

    void encrypt(byte[] bytes, ByteBuf out) throws Exception;

    void encrypt(ByteBuf bytes, ByteBuf out) throws Exception;

    byte[] decrypt(byte[] bytes) throws Exception;

    ByteBuf decrypt(ByteBuf bytes) throws Exception;


    default int paddingSize(){return 0;}
}
