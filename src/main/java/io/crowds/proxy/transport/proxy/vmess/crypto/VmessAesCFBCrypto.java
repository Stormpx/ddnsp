package io.crowds.proxy.transport.proxy.vmess.crypto;

import io.crowds.util.Bufs;
import io.crowds.util.Crypto;
import io.crowds.util.Hash;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.Arrays;

/**
 * legacy not support
 */
@Deprecated
public class VmessAesCFBCrypto implements VmessCrypto {

    private byte[] key;
    private byte[] iv;

    public VmessAesCFBCrypto(byte[] key, byte[] iv) {
        this.key = key;
        this.iv = iv;
    }

    @Override
    public void encrypt(byte[] bytes, ByteBuf out) throws Exception {
        int fnv1a32 = Hash.fnv1a32(bytes, bytes.length);
        ByteBuf buf = Unpooled.buffer(bytes.length + 4+2, bytes.length + 4+2)
                .writeShort(bytes.length + 4).writeInt(fnv1a32).writeBytes(bytes);
        byte[] plainText= buf.array();
        byte[] encrypt = Crypto.aes128CFBEncrypt(key, iv, plainText);

        out.writeBytes(encrypt);
    }

    @Override
    public void encrypt(ByteBuf bytes, ByteBuf out) throws Exception {

    }


    @Override
    public byte[] decrypt(byte[] bytes) throws Exception{
        byte[] decrypt = Crypto.aes128CFBDecrypt(key, iv, bytes);
        int remoteFnv1a32=Bufs.getInt(decrypt,0);

        int f = Hash.fnv1a32(decrypt, 4,decrypt.length - 4);
        if (f!=remoteFnv1a32){
            throw new RuntimeException("fnv1a32 not match");
        }
        byte[] content = Arrays.copyOfRange(decrypt, 4, decrypt.length);
        return content;
    }

    @Override
    public ByteBuf decrypt(ByteBuf bytes) throws Exception {
        return null;
    }


}
