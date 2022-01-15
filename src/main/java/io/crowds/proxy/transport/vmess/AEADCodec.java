package io.crowds.proxy.transport.vmess;

import io.crowds.util.ByteBufCipher;
import io.crowds.util.Crypto;
import io.crowds.util.Hash;
import io.crowds.util.Rands;
import io.netty.buffer.*;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.ExtendedDigest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.macs.KMAC;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import javax.crypto.Cipher;
import java.util.UUID;


public class AEADCodec {
    public final static String KDFSaltConstAuthIDEncryptionKey             = "AES Auth ID Encryption";
    public final static String KDFSaltConstAEADRespHeaderLenKey            = "AEAD Resp Header Len Key";
    public final static String KDFSaltConstAEADRespHeaderLenIV             = "AEAD Resp Header Len IV";
    public final static String KDFSaltConstAEADRespHeaderPayloadKey        = "AEAD Resp Header Key";
    public final static String KDFSaltConstAEADRespHeaderPayloadIV         = "AEAD Resp Header IV";
    public final static String KDFSaltConstVMessAEADKDF                    = "VMess AEAD KDF";
    public final static String KDFSaltConstVMessHeaderPayloadAEADKey       = "VMess Header AEAD Key";
    public final static String KDFSaltConstVMessHeaderPayloadAEADIV        = "VMess Header AEAD Nonce";
    public final static String KDFSaltConstVMessHeaderPayloadLengthAEADKey = "VMess Header AEAD Key_Length";
    public final static String KDFSaltConstVMessHeaderPayloadLengthAEADIV  = "VMess Header AEAD Nonce_Length";

    private byte[] buffer32;
    private byte[] buffer16;
    private byte[] buffer12;

    public AEADCodec() {
        this.buffer32=new byte[32];
        this.buffer16=new byte[16];
        this.buffer12=new byte[12];
    }

    private byte[] kdfBase(byte[] key, byte[] out, byte[]... path) {
        HMac hMac = new HMac(new SHA256Digest());
        hMac.init(new KeyParameter(KDFSaltConstVMessAEADKDF.getBytes()));
        for (byte[] bytes : path) {
            hMac=new HMac(new HmacDigest(hMac));
            hMac.init(new KeyParameter(bytes));
        }
        hMac.update(key,0,key.length);
        hMac.doFinal(out,0);
        return out;
    }

    private byte[] kdf32(byte[] key,byte[]... path) {
        return kdfBase(key,buffer32,path);
    }

    private byte[] kdf16(byte[] key, byte[]... path) {
        byte[] kdf32 = kdf32(key, path);
        byte[] r = buffer16;
        System.arraycopy(kdf32,0,r,0,16);
        return r;
    }
    private byte[] kdf12(byte[] key,byte[]... path){
        byte[] kdf32 = kdf32(key, path);
        byte[] r = buffer12;
        System.arraycopy(kdf32,0,r,0,12);
        return r;
    }


    public void encryptAEADReqHeader(byte[] cmdKey, ByteBuf headerPlain, ByteBuf out) throws Exception {
        //write EAuID
        ByteBuf eAuIdPlain = out.alloc().buffer(16).writeLong(System.currentTimeMillis()/1000).writeInt(Rands.nextInt());
        eAuIdPlain.writeInt((int) Hash.crc32(eAuIdPlain.nioBuffer()));

        byte[] key = kdf16(cmdKey, KDFSaltConstAuthIDEncryptionKey.getBytes());
//        ByteBuf eAuId = Unpooled.buffer(16);
        byte[] eAuId=new byte[16];
        ByteBuf buf = ByteBufCipher.doFinal(Crypto.getCipher(key, true), eAuIdPlain, out.alloc());
        buf.readBytes(eAuId);

        out.writeBytes(eAuId);

        buf.release();
        eAuIdPlain.release();

        byte[] nonce = Rands.genBytes(8);
        //length
        {
            int headerLength = headerPlain.readableBytes();
            byte[] headerLengthKey = kdf16(cmdKey, AEADCodec.KDFSaltConstVMessHeaderPayloadLengthAEADKey.getBytes(), eAuId, nonce);
            byte[] headerLengthIv= kdf12(cmdKey,AEADCodec.KDFSaltConstVMessHeaderPayloadLengthAEADIV.getBytes(),eAuId,nonce);
            Cipher gcmCipher = Crypto.getGcmCipher(headerLengthKey,headerLengthIv, true);
            gcmCipher.updateAAD(eAuId);
            ByteBufCipher.doFinal(gcmCipher, Unpooled.buffer(2).writeShort(headerLength), out);
        }
        out.writeBytes(nonce);
        //header
        {
            byte[] headerPayloadKey = kdf16(cmdKey, AEADCodec.KDFSaltConstVMessHeaderPayloadAEADKey.getBytes(), eAuId, nonce);
            byte[] headerPayloadIv= kdf12(cmdKey,AEADCodec.KDFSaltConstVMessHeaderPayloadAEADIV.getBytes(),eAuId,nonce);
            Cipher gcmCipher = Crypto.getGcmCipher(headerPayloadKey,headerPayloadIv, true);
            gcmCipher.updateAAD(eAuId);
            ByteBufCipher.doFinal(gcmCipher,headerPlain, out);
        }
    }


    public Integer decryptAEADRespHeaderLength(byte[] responseKey, byte[] responseIv, ByteBuf cipherLength, ByteBufAllocator allocator) throws Exception {
        if (cipherLength.readableBytes()<18){
            throw new IllegalStateException("unable to read header len. readableBytes<18 ");
        }
        if (cipherLength.readableBytes()>18){
            cipherLength=cipherLength.readSlice(18);
        }
        byte[] headerLengthKey = kdf16(responseKey, KDFSaltConstAEADRespHeaderLenKey.getBytes());
        byte[] headerLengthIv = kdf12(responseIv, KDFSaltConstAEADRespHeaderLenIV.getBytes());
        Cipher gcmCipher = Crypto.getGcmCipher(headerLengthKey,headerLengthIv, false);
        ByteBuf lengthBuf = ByteBufCipher.doFinal(gcmCipher, cipherLength, allocator);
        int r = lengthBuf.readUnsignedShort() + 16;
        lengthBuf.release();
        return r;
    }

    public ByteBuf decryptAEADRespHeader(byte[] responseKey, byte[] responseIv, ByteBuf cipher, ByteBufAllocator allocator) throws Exception {
        byte[] headerPayloadKey = kdf16(responseKey, KDFSaltConstAEADRespHeaderPayloadKey.getBytes());
        byte[] headerPayloadIv = kdf12(responseIv, KDFSaltConstAEADRespHeaderPayloadIV.getBytes());
        Cipher gcmCipher = Crypto.getGcmCipher(headerPayloadKey,headerPayloadIv, false);
        return ByteBufCipher.doFinal(gcmCipher, cipher, allocator);
    }



    //未曾设想的道路...
    public class HmacDigest implements ExtendedDigest {
        private final HMac hMac;

        public HmacDigest(HMac hMac) {
            this.hMac = hMac;
        }

        @Override
        public String getAlgorithmName() {
            return "hmac";
        }

        @Override
        public int getDigestSize() {
            return hMac.getMacSize();
        }

        @Override
        public void update(byte in) {
            hMac.update(in);
        }

        @Override
        public void update(byte[] in, int inOff, int len) {
            hMac.update(in, inOff, len);
        }

        @Override
        public int doFinal(byte[] out, int outOff) {
            return hMac.doFinal(out,outOff);
        }

        @Override
        public void reset() {
            hMac.reset();
        }

        @Override
        public int getByteLength() {
            return 64;
        }
    }
}
