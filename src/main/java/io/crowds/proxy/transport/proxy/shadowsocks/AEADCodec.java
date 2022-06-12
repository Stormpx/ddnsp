package io.crowds.proxy.transport.proxy.shadowsocks;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.common.Socks;
import io.crowds.proxy.transport.Destination;
import io.crowds.util.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.ReferenceCountUtil;

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


    public static class  TcpCodec extends ByteToMessageCodec<Object> {
        protected ShadowsocksOption shadowsocksOption;
        protected Cipher cipher;

        protected boolean sendSalt=false;
        protected byte[] encryptSalt;
        protected byte[] encryptSubKey;
        protected byte[] encryptNonce;


        protected Integer expectLength=18;
        protected Integer expectPayloadLength;
        protected byte[] decryptSalt;
        protected byte[] decryptSubKey;
        protected byte[] decryptNonce;


        public TcpCodec(ShadowsocksOption shadowsocksOption) {
            this.shadowsocksOption = shadowsocksOption;
            this.cipher=shadowsocksOption.getCipher();
            init();
        }

        private void init(){
            this.encryptSalt = AEAD.genSalt(this.cipher);
            this.encryptSubKey =AEAD.genSubKey(this.cipher,shadowsocksOption.getMasterKey(),this.encryptSalt);

            this.encryptNonce= new byte[12];
            Arrays.fill(encryptNonce, (byte) 0xff);
            this.decryptNonce=new byte[12];
            Arrays.fill(decryptNonce, (byte) 0xff);
        }

        protected byte[] incrementedAndGet(byte[] nonce){
            for (int i = 0; i < nonce.length; i++) {
                nonce[i]++;
                if (nonce[i]!=0)
                    break;
            }
            return nonce;
        }

        protected int maxPayloadLength(){
            return 0x3fff;
        }

        @Override
        public boolean acceptOutboundMessage(Object msg) throws Exception {
            return msg instanceof ByteBuf || msg instanceof ShadowsocksRequest;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
            if (!sendSalt){
                out.writeBytes(this.encryptSalt);
                sendSalt=true;
            }
            if (msg instanceof ShadowsocksRequest request){
                Destination destination = request.getDestination();
                Object payload = request.getPayload();
                ByteBuf addrBuf = ctx.alloc().buffer();
                Socks.encodeAddr(destination.addr(),addrBuf);
                if (payload==null){
                    encode(ctx,addrBuf,out);
                    ReferenceCountUtil.safeRelease(addrBuf);
                }else {
                    assert payload instanceof ByteBuf;
                    ByteBuf content = Unpooled.compositeBuffer()
                            .addComponent(true,addrBuf)
                            .addComponent(true, (ByteBuf) payload);
                    encode(ctx,content,out);
                    ReferenceCountUtil.safeRelease(content);
                }
                return;
            }
            if (msg instanceof ByteBuf buf){
                if (!buf.isReadable()){
                    return;
                }
                int readBytes=Math.min(maxPayloadLength(),buf.readableBytes());
                //encode len
                byte[] lenBytes=new byte[2];
                Bufs.writeShort(lenBytes,0,readBytes);
                ByteBufCipher.doFinal(
                        AEAD.getEncryptCipher(this.cipher, this.encryptSubKey, incrementedAndGet(this.encryptNonce)),
                        Unpooled.wrappedBuffer(lenBytes),out
                );

                //encode payload
                ByteBufCipher.doFinal(
                        AEAD.getEncryptCipher(this.cipher, this.encryptSubKey, incrementedAndGet(this.encryptNonce)),
                        buf.readSlice(readBytes),out
                );


                if (buf.isReadable()){
                    encode(ctx, buf, out);
                }
            }
        }


        private ByteBuf readAndDecrypt(ChannelHandlerContext ctx,ByteBuf buf,int length) throws Exception {
            return ByteBufCipher.doFinal(
                    AEAD.getDecryptCipher(this.cipher,this.decryptSubKey,incrementedAndGet(this.decryptNonce)),
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
                this.decryptSubKey=AEAD.genSubKey(this.cipher,shadowsocksOption.getMasterKey(),this.decryptSalt);
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
            if (in.isReadable()) {
                decode(ctx, in, out);
            }

        }
    }

    public static class UdpCodec extends MessageToMessageCodec<DatagramPacket,ShadowsocksRequest>{
        private final static byte[] ALWAYS_ZERO=new byte[12];
        private ShadowsocksOption shadowsocksOption;
        private Cipher cipher;

        public UdpCodec(ShadowsocksOption shadowsocksOption) {
            this.shadowsocksOption = shadowsocksOption;
            this.cipher=shadowsocksOption.getCipher();
        }


        @Override
        protected void encode(ChannelHandlerContext ctx, ShadowsocksRequest msg, List<Object> out) throws Exception {
            byte[] salt = AEAD.genSalt(this.cipher);
            byte[] subKey = AEAD.genSubKey(this.cipher, shadowsocksOption.getMasterKey(), salt);

            Destination destination = msg.getDestination();
            Object payload = msg.getPayload();
            assert payload instanceof DatagramPacket;
            DatagramPacket packet= (DatagramPacket) payload;

            ByteBuf addrBuf = ctx.alloc().buffer(32);
            Socks.encodeAddr(NetAddr.of(packet.recipient()), addrBuf);

            ByteBuf content = Unpooled.compositeBuffer()
                    .addComponent(true,addrBuf)
                    .addComponent(true,packet.content());

            ByteBuf buf = ctx.alloc().buffer(salt.length+content.readableBytes()+16);
            buf.writeBytes(salt);
            ByteBufCipher.doFinal(
                    AEAD.getEncryptCipher(this.cipher, subKey, ALWAYS_ZERO),
                    content,
                    buf
            );

            ReferenceCountUtil.safeRelease(content);
            out.add(new DatagramPacket(buf,destination.addr().getAsInetAddr()));
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) throws Exception {
            ByteBuf buf = msg.content();
            int saltSize=this.cipher.getSaltSize();
            if (buf.readableBytes()<saltSize){
                return;
            }
            byte[] salt=new byte[saltSize];
            buf.readBytes(salt);

            byte[] subKey = AEAD.genSubKey(this.cipher, shadowsocksOption.getMasterKey(), salt);
            ByteBuf plain = ByteBufCipher.doFinal(AEAD.getDecryptCipher(this.cipher, subKey, ALWAYS_ZERO),buf,ctx.alloc());

            InetSocketAddress sender = Socks.decodeAddr(plain);
            plain.discardReadBytes();
            out.add(new DatagramPacket(plain,null,sender));
        }
    }
}
