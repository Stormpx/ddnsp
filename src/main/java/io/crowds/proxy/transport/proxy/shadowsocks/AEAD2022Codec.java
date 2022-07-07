package io.crowds.proxy.transport.proxy.shadowsocks;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.common.Socks;
import io.crowds.proxy.transport.Destination;
import io.crowds.util.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ReferenceCountUtil;

import javax.crypto.Cipher;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;

public class AEAD2022Codec {


    public static class TCP2022Codec extends AEADCodec.TcpCodec{
        private final SaltPool saltPool;
        private boolean decryptFail;

        public TCP2022Codec(ShadowsocksOption shadowsocksOption, SaltPool saltPool) {
            super(shadowsocksOption);
            Objects.requireNonNull(saltPool);
            this.saltPool = saltPool;
        }

        protected int maxPayloadLength(){
            return 0xFFFF;
        }

        private ByteBuf encodeVariableHeader(ChannelHandlerContext ctx,ShadowsocksRequest request){
            Destination destination = request.getDestination();
            Object payload = request.getPayload();

            ByteBuf buffer = ctx.alloc().buffer(32);
            int maxLength=0xFFFF;
            //address
            Socks.encodeAddr(destination.addr(),buffer);
            //padding
            int paddingLength = Rands.nextInt(0, 901);
            buffer.writeShort(paddingLength);
            for (int i = 0; i < paddingLength; i++) {
                buffer.writeByte(Rands.nextByte());
            }

            maxLength-=buffer.readableBytes();

            if (payload!=null){
                //initial payload
                assert payload instanceof ByteBuf;
                ByteBuf buf= (ByteBuf) payload;
                maxLength=Math.min(maxLength,buf.readableBytes());
                if (maxLength>0)
                    buffer.writeBytes(buf.readSlice(maxLength));
            }

            return buffer;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
            if (!sendSalt){
                out.writeBytes(this.encryptSalt);
                sendSalt=true;
            }
            if (msg instanceof ShadowsocksRequest request){
                ByteBuf variableHeader = encodeVariableHeader(ctx, request);

                long timestamp = System.currentTimeMillis()/1000;
                ByteBuf fixedHeader = ctx.alloc().buffer(11);
                fixedHeader.writeByte(0).writeLong(timestamp).writeShort(variableHeader.readableBytes());
                ByteBufCipher.doFinal(
                        AEAD.getEncryptCipher(this.cipherAlgo, this.encryptSubKey, incrementedAndGet(this.encryptNonce)),
                        fixedHeader,out
                );
                ReferenceCountUtil.safeRelease(fixedHeader);

                ByteBufCipher.doFinal(
                        AEAD.getEncryptCipher(this.cipherAlgo, this.encryptSubKey, incrementedAndGet(this.encryptNonce)),
                        variableHeader,out
                );
                ReferenceCountUtil.safeRelease(variableHeader);

                Object payload = request.getPayload();
                if (payload!=null){
                    assert payload instanceof ByteBuf;
                    super.encode(ctx, payload, out);
                    if (!((ByteBuf) payload).isReadable()){
                        ReferenceCountUtil.safeRelease(payload);
                    }
                }
                return;
            }
            super.encode(ctx, msg, out);
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            try {
                if (this.decryptFail){
                    in.skipBytes(in.readableBytes());
                    return;
                }
                if (this.decryptSalt==null){
                    //response salt
                    int expectLength= cipherAlgo.getSaltSize();
                    //fixed-length header
                    expectLength+=11+ cipherAlgo.getSaltSize()+16;
                    if (in.readableBytes()<expectLength){
                        throw new ShadowsocksException("data not enough for decryption");
                    }
                    if (!readDecryptSalt(in)){
                        throw new ShadowsocksException("unable read decrypt salt");
                    }
                    long now = System.currentTimeMillis()/1000;
                    if (!this.saltPool.against(this.decryptSalt,now)){
                        throw new ShadowsocksException("repeat salt detected");
                    }

                    ByteBuf fixedHeader = readAndDecrypt(ctx, in, expectLength- cipherAlgo.getSaltSize());
                    byte type = fixedHeader.readByte();
                    if (type !=1){
                        throw new ShadowsocksException("headerTypeServerStream = "+type);
                    }
                    long timestamp = fixedHeader.readLong();
                    if (Ints.diff(now,timestamp) > 30){
                        throw new ShadowsocksException("bad timestamp");
                    }
                    ByteBuf salt = fixedHeader.readSlice(cipherAlgo.getSaltSize());
                    if (!salt.equals(Unpooled.wrappedBuffer(this.encryptSalt))){
                        throw new ShadowsocksException("bad request salt");
                    }
                    int expectPayloadLength= fixedHeader.readUnsignedShort();
                    if (expectPayloadLength>0)
                        this.expectPayloadLength=expectPayloadLength+16;
                }

                super.decode(ctx, in, out);

            } catch (Exception e) {
                decryptFail=true;
                throw e;
            }
        }
    }

    public static class Udp2022Codec extends AEADCodec.UdpCodec{
        private final byte[] buffer=new byte[16];
        private UdpSession session;
        private Cipher encryptBlockCipher;
        private Cipher decryptBlockCipher;

        private UdpSession remoteOldSession;
        private UdpSession remoteSession;

        public Udp2022Codec(ShadowsocksOption shadowsocksOption) {
            super(shadowsocksOption);
            init();
        }

        private UdpSession newSession(){
            return newSession(Rands.nextLong());
        }

        private UdpSession newSession(long sessionId){
            byte[] b=new byte[8];
            Bufs.writeLong(b,0,sessionId);
            byte[] subKey=AEAD.genSubKey(cipherAlgo,shadowsocksOption.getMasterKey(),b);
            return new UdpSession(sessionId,subKey);
        }


        private void init() {
            try {
                this.encryptBlockCipher = Crypto.getAESWithoutPaddingCipher(shadowsocksOption.getMasterKey(),true);
                this.decryptBlockCipher = Crypto.getAESWithoutPaddingCipher(shadowsocksOption.getMasterKey(),false);
                this.session=newSession();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }

        private ByteBuf encodeMainHeader(ByteBufAllocator allocator, NetAddr dstAddr){
            ByteBuf buffer = allocator.buffer();
            buffer.writeByte(0)
                    .writeLong(System.currentTimeMillis()/1000);

            int paddingLength = Rands.nextInt(0, 901);
            buffer.writeShort(paddingLength);
            for (int i = 0; i < paddingLength; i++) {
                buffer.writeByte(Rands.nextByte());
            }
            Socks.encodeAddr(dstAddr,buffer);

            return buffer;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, ShadowsocksRequest msg, List<Object> out) throws Exception {
            assert msg.getPayload() instanceof DatagramPacket;
            Destination destination = msg.getDestination();
            DatagramPacket packet= (DatagramPacket) msg.getPayload();

            long sessionId = this.session.id;
            long packetId = this.session.packetId++;
            ByteBuf separateHeader = Unpooled.wrappedBuffer(this.buffer)
                    .resetReaderIndex().resetWriterIndex();
            separateHeader.writeLong(sessionId).writeLong(packetId);
            ByteBuf output = ByteBufCipher.doFinal(this.encryptBlockCipher, separateHeader, ctx.alloc());

            assert output.readableBytes()==16;

            ByteBuf mainHeader = encodeMainHeader(ctx.alloc(), NetAddr.of(packet.recipient()));
            mainHeader.writeBytes(packet.content());

            session.encrypt(packetId,cipherAlgo,mainHeader,output);

            ReferenceCountUtil.safeRelease(mainHeader);
            out.add(new DatagramPacket(output,destination.addr().getAsInetAddr()));
        }




        private UdpSession getSession(long sessionId){
            if (this.remoteSession !=null&&Objects.equals(this.remoteSession.id,sessionId)){
                return this.remoteSession;
            }
            if (this.remoteOldSession!=null&&Objects.equals(this.remoteOldSession.id,sessionId)){
                return this.remoteOldSession;
            }
            return null;
        }


        @Override
        protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) throws Exception {
            ByteBuf content = msg.content();
            ByteBuf separateHeader = ByteBufCipher.doFinal(this.decryptBlockCipher, content.readSlice(16));
            long sessionId = separateHeader.readLong();
            long packetId = separateHeader.readLong();
            long now = System.currentTimeMillis()/1000;

            UdpSession currentSession=getSession(sessionId);
            if (currentSession==null){
                //new session
                if (remoteSession ==null){
                    this.remoteSession =newSession(sessionId);
                }else if (remoteOldSession==null){
                    this.remoteOldSession= remoteSession;
                    this.remoteSession =newSession(sessionId);
                }else{
                    if (Ints.diff(now,this.remoteOldSession.lastTimestamp)<60){
                        //reject
                        return;
                    }
                    this.remoteOldSession=this.remoteSession;
                    this.remoteSession =newSession(sessionId);
                }
                currentSession=this.remoteSession;
            }

            if (!currentSession.receivePacket(packetId,now)){
                //reject
                return;
            }

            ByteBuf plain = currentSession.decrypt(cipherAlgo, content, packetId);
            try {
                if (plain.readByte()!=1){
                    throw new ShadowsocksException("bad header type");
                }
                long timestamp = plain.readLong();
                if (Ints.diff(now,timestamp) >30){
                    throw new ShadowsocksException("bad timestamp");
                }
                //client sessionid
                long clientSessionId=plain.readLong();
                if (!Objects.equals(clientSessionId,this.session.id)){
                    throw new ShadowsocksException("bad client session id");
                }
                //skip padding
                plain.skipBytes(plain.readUnsignedShort());

                InetSocketAddress sender = Socks.decodeAddr(plain);
                plain.discardReadBytes();

                out.add(new DatagramPacket(plain,null,sender));
            } catch (Exception e){
                ReferenceCountUtil.safeRelease(plain);
                throw e;
            }

            //swap
            if (currentSession==this.remoteOldSession){
                this.remoteOldSession=this.remoteSession;
                this.remoteSession =currentSession;
            }
        }
    }

    public static class UdpSession {
        private final byte[] nonce=new byte[12];
        private final byte[] subKey;
        private final long id;
        private long packetId;
        private long lastTimestamp;
        private ReplayWindow window;

        public UdpSession(long id, byte[] subKey) {
            this.id = id;
            this.subKey = subKey;
            this.packetId=0;
        }

        public boolean receivePacket(long packetId,long timestamp){
            if (this.window==null)
                this.window=new ReplayWindow(1<<4);
            // sliding window filter
            if (!this.window.update(packetId)){
                return false;
            }


            this.lastTimestamp=timestamp;
            return true;
        }

        public void encrypt(long packetId,CipherAlgo cipherAlgo,ByteBuf in,ByteBuf out) throws Exception {
            Bufs.writeInt(nonce,0, (int) id);
            Bufs.writeLong(nonce,4,packetId);
            ByteBufCipher.doFinal(AEAD.getEncryptCipher(cipherAlgo,subKey,nonce),in,out);
        }

        public ByteBuf decrypt(CipherAlgo cipherAlgo,ByteBuf in,long packetId) throws Exception {
            Bufs.writeInt(nonce,0, (int) id);
            Bufs.writeLong(nonce,4,packetId);
            return ByteBufCipher.doFinal(AEAD.getDecryptCipher(cipherAlgo,subKey,nonce),in);
        }


    }


}
