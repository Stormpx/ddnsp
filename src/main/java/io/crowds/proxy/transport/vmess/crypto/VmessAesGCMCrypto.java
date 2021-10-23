package io.crowds.proxy.transport.vmess.crypto;

import io.crowds.util.Bufs;
import io.crowds.util.Crypto;
import io.netty.buffer.ByteBuf;

public class VmessAesGCMCrypto implements VmessCrypto {

    private byte[] key;
    private byte[] iv;

    private short count=0;


    public VmessAesGCMCrypto(byte[] key, byte[] iv) {
        this.key = key;
        byte[] cryptoIv=new byte[12];
        System.arraycopy(iv,2,cryptoIv,2,10);
        this.iv=cryptoIv;
    }
    private byte[] getIv(){
        Bufs.writeShort(this.iv,0, count++);
        return this.iv;
    }


    @Override
    public void encrypt(byte[] bytes, ByteBuf out) throws Exception {
        byte[] encrypt = Crypto.gcmEncrypt(bytes, key, getIv());
        out.writeBytes(encrypt);
    }

    @Override
    public byte[] decrypt(byte[] bytes) throws Exception {

        return Crypto.gcmDecrypt(bytes,key,getIv());
    }

    @Override
    public int getMaxSize() {
        return 16384-16;
    }
}
