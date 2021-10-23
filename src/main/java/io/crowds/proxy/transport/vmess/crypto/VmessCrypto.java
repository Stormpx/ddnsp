package io.crowds.proxy.transport.vmess.crypto;

import io.netty.buffer.ByteBuf;


public interface VmessCrypto {

    void encrypt(byte[] bytes, ByteBuf out) throws Exception;


    byte[] decrypt(byte[] bytes) throws Exception;


    default int getMaxSize(){
        return 16384;
    }
}
