package io.crowds.proxy.transport.proxy.vmess.crypto;

import io.crowds.util.Bufs;
import io.crowds.util.ByteBufCipher;
import io.crowds.util.Crypto;
import io.netty.buffer.ByteBuf;

import javax.crypto.Cipher;

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
    public void encrypt(ByteBuf bytes, ByteBuf out) throws Exception {
        Cipher cipher = Crypto.getGcmCipher(key, getIv(), true);
        ByteBufCipher.doFinal(cipher,bytes,out);
//        int writeBytes = bytes.readableBytes()+paddingSize();
//        out.ensureWritable(writeBytes);
//        int writerIndex = out.writerIndex();
//        ByteBuffer outBuffer = out.nioBuffer(writerIndex, writeBytes);
//        Crypto.gcmEncrypt(bytes.nioBuffer(),key,getIv(),outBuffer);
//        out.writerIndex(writerIndex+writeBytes);
    }

    @Override
    public byte[] decrypt(byte[] bytes) throws Exception {

        return Crypto.gcmDecrypt(bytes,key,getIv());
    }

    @Override
    public ByteBuf decrypt(ByteBuf bytes) throws Exception {
        Cipher cipher = Crypto.getGcmCipher(key, getIv(), false);
        return ByteBufCipher.doFinal(cipher,bytes);
//        int i = bytes.readableBytes() - paddingSize();
//        ByteBuf out = bytes.alloc().directBuffer(i);
//        Crypto.gcmDecrypt(bytes.nioBuffer(),key,getIv(),out.nioBuffer(0,i));
//        out.writerIndex(i);
//        return out;
    }

    @Override
    public int paddingSize() {
        return 16;
    }
}
