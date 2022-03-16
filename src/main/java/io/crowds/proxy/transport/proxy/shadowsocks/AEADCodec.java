package io.crowds.proxy.transport.proxy.shadowsocks;

import io.crowds.proxy.common.Socks;
import io.crowds.util.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.handler.codec.MessageToMessageCodec;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

public class AEADCodec {

    public static TcpCodec tcp(ShadowsocksOption option){
        return new TcpCodec(option);
    }

    public static UdpCodec udp(ShadowsocksOption option){
        return new UdpCodec(option);
    }

    private static byte[] genSalt(Cipher cipher){
        return Rands.genBytes(Math.max(16, cipher.getSaltSize() ));
    }

    private static byte[] genSubKey(Cipher cipher,byte[] masterKey,byte[] salt){
        return Hash.hkdfSHA1(masterKey,salt,"ss-subkey".getBytes(),cipher.getKeySize());
    }
    private static javax.crypto.Cipher getEncryptCipher(Cipher cipher,byte[] key,byte[] nonce) throws Exception {
        return switch (cipher) {
            case CHACHA20_IETF_POLY1305 -> Crypto.getChaCha20Poly1305Cipher( key, nonce,true);
            case AES_256_GCM, AES_192_GCM, AES_128_GCM -> Crypto.getGcmCipher(key, nonce,true);
        };
    }

    private static byte[] encrypt(Cipher cipher,byte[] key,byte[] nonce,byte[] plain) throws Exception {
        return switch (cipher) {
            case CHACHA20_IETF_POLY1305 -> Crypto.chaCha20Poly1305Encrypt(plain, key, nonce);
            case AES_256_GCM, AES_192_GCM, AES_128_GCM -> Crypto.gcmEncrypt(plain, key, nonce);
        };
    }

    private static javax.crypto.Cipher getDecryptCipher(Cipher cipher,byte[] key,byte[] nonce) throws Exception {
        return switch (cipher) {
            case CHACHA20_IETF_POLY1305 -> Crypto.getChaCha20Poly1305Cipher( key, nonce,false);
            case AES_256_GCM, AES_192_GCM, AES_128_GCM -> Crypto.getGcmCipher(key, nonce,false);
        };
    }

    private static byte[] decrypt(Cipher cipher,byte[] key,byte[] nonce,byte[] plain) throws Exception {
        return switch (cipher) {
            case CHACHA20_IETF_POLY1305 -> Crypto.chaCha20Poly1305Decrypt(plain, key, nonce);
            case AES_256_GCM, AES_192_GCM, AES_128_GCM -> Crypto.gcmDecrypt(plain, key, nonce);
        };
    }


    public static class  TcpCodec extends ByteToMessageCodec<ByteBuf> {
        private ShadowsocksOption shadowsocksOption;
        private Cipher cipher;

        private boolean sendSalt=false;
        private byte[] encryptSalt;
        private byte[] encryptSubKey;
        private byte[] encryptNonce;


        private Integer expectLength=18;
        private Integer expectPayloadLength;
        private byte[] decryptSalt;
        private byte[] decryptSubKey;
        private byte[] decryptNonce;


        public TcpCodec(ShadowsocksOption shadowsocksOption) {
            this.shadowsocksOption = shadowsocksOption;
            this.cipher=shadowsocksOption.getCipher();
            init();
        }

        private void init(){
            this.encryptSalt = genSalt(this.cipher);
            this.encryptSubKey =genSubKey(this.cipher,shadowsocksOption.getMasterKey(),this.encryptSalt);

            this.encryptNonce= new byte[12];
            Arrays.fill(encryptNonce, (byte) 0xff);
            this.decryptNonce=new byte[12];
            Arrays.fill(decryptNonce, (byte) 0xff);
        }

        private byte[] incrementedAndGet(byte[] nonce){
            for (int i = 0; i < nonce.length; i++) {
                nonce[i]++;
                if (nonce[i]!=0)
                    break;
            }
            return nonce;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
            if (!sendSalt){
                out.writeBytes(this.encryptSalt);
                sendSalt=true;
            }
            int maxLen=0x3fff-16;
            int readBytes=Math.min(maxLen,msg.readableBytes());
            //encode len
            byte[] lenBytes=new byte[2];
            Bufs.writeShort(lenBytes,0,readBytes);
            ByteBufCipher.doFinal(
                    getEncryptCipher(this.cipher, this.encryptSubKey, incrementedAndGet(this.encryptNonce)),
                    Unpooled.wrappedBuffer(lenBytes),out
            );

            //encode payload
            ByteBufCipher.doFinal(
                    getEncryptCipher(this.cipher, this.encryptSubKey, incrementedAndGet(this.encryptNonce)),
                    msg.readSlice(readBytes),out
            );

            if (msg.isReadable()){
                encode(ctx, msg, out);
            }

        }


        private ByteBuf readAndDecrypt(ChannelHandlerContext ctx,ByteBuf buf,int length) throws Exception {
//            byte[] encryptedBytes=new byte[length];
//            buf.readBytes(encryptedBytes);
//            return decrypt(this.cipher,this.decryptSubKey,incrementedAndGet(this.decryptNonce),encryptedBytes);

            return ByteBufCipher.doFinal(
                    getDecryptCipher(this.cipher,this.decryptSubKey,incrementedAndGet(this.decryptNonce)),
                    buf.readSlice(length),ctx.alloc()
            );
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            if (this.decryptSalt==null){
                if (in.readableBytes()<this.cipher.getSaltSize()){
                    return;
                }
                this.decryptSalt=new byte[this.cipher.getSaltSize()];
                in.readBytes(this.decryptSalt);
                this.decryptSubKey=genSubKey(this.cipher,shadowsocksOption.getMasterKey(),this.decryptSalt);
            }
            if (this.expectPayloadLength==null){
                if (in.readableBytes()<this.expectLength){
                    return;
                }

                ByteBuf decryptLenBytes = readAndDecrypt(ctx,in,this.expectLength);
                this.expectPayloadLength = decryptLenBytes.readUnsignedShort();
                this.expectPayloadLength+=16;
                decryptLenBytes.release();
            }

            if (in.readableBytes()<this.expectPayloadLength){
                return;
            }

            ByteBuf payload = readAndDecrypt(ctx,in,this.expectPayloadLength);
            out.add(payload);
            this.expectPayloadLength=null;
            if (in.isReadable())
                decode(ctx, in, out);

        }
    }

    public static class UdpCodec extends MessageToMessageCodec<DatagramPacket,DatagramPacket>{
        private final static byte[] ALWAYS_ZERO=new byte[12];
        private ShadowsocksOption shadowsocksOption;
        private Cipher cipher;

        public UdpCodec(ShadowsocksOption shadowsocksOption) {
            this.shadowsocksOption = shadowsocksOption;
            this.cipher=shadowsocksOption.getCipher();
        }


        @Override
        protected void encode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) throws Exception {
            try {
                byte[] salt = genSalt(this.cipher);
                byte[] subKey = genSubKey(this.cipher, shadowsocksOption.getMasterKey(), salt);
                ByteBuf buf = ctx.alloc().buffer(salt.length+msg.content().readableBytes()+16);
                buf.writeBytes(salt);
                ByteBufCipher.doFinal(
                        getEncryptCipher(this.cipher, subKey, ALWAYS_ZERO),
                        msg.content(),buf
                        );

                out.add(new DatagramPacket(buf,msg.recipient()));

            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        @Override
        protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) throws Exception {
            int saltSize=this.cipher.getSaltSize();
            byte[] salt=new byte[saltSize];
            ByteBuf buf = msg.content();
            buf.readBytes(salt);
            byte[] subKey = genSubKey(this.cipher, shadowsocksOption.getMasterKey(), salt);
            ByteBuf plain = ByteBufCipher.doFinal(getDecryptCipher(this.cipher, subKey, ALWAYS_ZERO),buf,ctx.alloc());

            InetSocketAddress sender = Socks.decodeAddr(plain);
            ByteBuf content = plain.copy();
            plain.release();
            out.add(new DatagramPacket(content,null,sender));
        }
    }
}
