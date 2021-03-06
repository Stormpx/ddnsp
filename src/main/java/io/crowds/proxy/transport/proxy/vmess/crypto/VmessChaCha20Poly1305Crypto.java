package io.crowds.proxy.transport.proxy.vmess.crypto;

import io.crowds.util.Bufs;
import io.crowds.util.ByteBufCipher;
import io.crowds.util.Crypto;
import io.crowds.util.Hash;
import io.netty.buffer.ByteBuf;

import javax.crypto.Cipher;

public class VmessChaCha20Poly1305Crypto implements VmessCrypto {

    private byte[] key;
    private byte[] iv;

    private short encryptCount=0;

    private short decryptCount=0;

    public VmessChaCha20Poly1305Crypto(byte[] key, byte[] iv) {
        byte[] md5Key = Hash.md5(key);
        byte[] md5md5Key = Hash.md5(md5Key);
        byte[] chaChaKey = new byte[32];
        System.arraycopy(md5Key,0,chaChaKey,0,16);
        System.arraycopy(md5md5Key,0,chaChaKey,16,16);
        this.key=chaChaKey;

        byte[] cryptoIv=new byte[12];
        System.arraycopy(iv,2,cryptoIv,2,10);
        this.iv=cryptoIv;
    }
    private byte[] getIv(boolean encrypt){
        Bufs.writeShort(this.iv,0, encrypt?encryptCount++:decryptCount++);
        return this.iv;
    }
    @Override
    public void encrypt(byte[] bytes, ByteBuf out) throws Exception {
        byte[] encrypt = Crypto.chaCha20Poly1305Encrypt(bytes, key, getIv(true));
        out.writeBytes(encrypt);
    }

    @Override
    public void encrypt(ByteBuf bytes, ByteBuf out) throws Exception {
        Cipher cipher = Crypto.getChaCha20Poly1305Cipher(key, getIv(true), true);
        ByteBufCipher.doFinal(cipher,bytes,out);
    }


    @Override
    public byte[] decrypt(byte[] bytes) throws Exception {
        return Crypto.chaCha20Poly1305Decrypt(bytes,key,getIv(false));
    }

    @Override
    public ByteBuf decrypt(ByteBuf bytes) throws Exception {
        Cipher cipher = Crypto.getChaCha20Poly1305Cipher(key, getIv(false), false);
        return ByteBufCipher.doFinal(cipher,bytes);
    }


    @Override
    public int paddingSize() {
        return 16;
    }
}
