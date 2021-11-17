package io.crowds.proxy.transport.vmess;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.vmess.crypto.*;
import io.crowds.util.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import org.bouncycastle.crypto.digests.SHAKEDigest;

import javax.crypto.*;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class VmessMessageCodec extends ByteToMessageCodec<Object> {



    private VmessSession session;

    private AEADCodec aeadCodec;
    private VmessCrypto requestCrypto;
    private VmessCrypto responseCrypto;

    private int state;
    private Integer expectHeaderLength;
    private Integer expectContentLength;

    private Mask requestMask;
    private Mask responseMask;


    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof ByteBuf||msg instanceof VmessRequest;
    }


    private void createCrypto(Security security){
        if (security==null)
            security=Security.NONE;

        byte[] requestKey = session.getRequestKey();
        byte[] responseKey = session.getResponseKey();
        byte[] requestIv = session.getRequestIv();
        byte[] responseIv = session.getResponseIv();
        if (this.session.isAEAD()){
            this.aeadCodec=new AEADCodec();
        }
        switch (security) {
            case NONE -> {
                this.requestCrypto = new VmessNoneCrypto();
                this.responseCrypto = new VmessNoneCrypto();
            }
            case AES_128_GCM -> {
                this.requestCrypto = new VmessAesGCMCrypto(requestKey, requestIv);
                this.responseCrypto = new VmessAesGCMCrypto(responseKey, responseIv);
            }
            case ChaCha20_Poly1305 -> {
                this.requestCrypto = new VmessChaCha20Poly1305Crypto(requestKey, requestIv);
                this.responseCrypto = new VmessChaCha20Poly1305Crypto(responseKey, responseIv);
            }
            default -> throw new RuntimeException("");
        }
    }

    private void encodeOptions(Set<Option> options, ByteBuf out){
        byte b=0;
        if (options!=null){
            for (Option option : options) {
                b|=option.getValue();
            }
        }
        out.writeByte(b);
    }


    private void encodeRequest(VmessRequest request,ByteBuf out) throws Exception {
        VmessUser user = request.getUser();
        UUID uuid = user.getUuid();

        long timestmap = Instant.now().getEpochSecond();
        this.session=VmessSession.create(user,timestmap);
        this.session.setOpts(request.getOpts());
        createCrypto(request.getSecurity());

        if (!this.session.isAEAD()) {
            byte[] auth = Hash.hmac(
                    ByteBufUtil.getBytes(Unpooled.buffer(8).writeLong(timestmap), 0, 8, false),
                    ByteBufUtil.getBytes(Unpooled.buffer(16).writeLong(uuid.getMostSignificantBits()).writeLong(uuid.getLeastSignificantBits()), 0, 16, false),
                    "HmacMD5");
            out.writeBytes(auth);
        }

        ByteBuf plain=out.alloc().buffer();
        //start
        plain.writeByte(1);

        plain.writeBytes(session.getRequestIv())
                .writeBytes(session.getRequestKey())
                .writeByte(session.getResponseHeader());


        encodeOptions(request.getOpts(),plain);

        int p = (ThreadLocalRandom.current().nextInt()&0b1111);
        plain.writeByte((p<<4)|request.getSecurity().getValue());
        plain.writeByte(0);
        plain.writeByte(request.getCmd()== TP.TCP?1:2);

        NetAddr addr = request.getAddr();
        byte[] addrBytes = addr.getByte();
        plain.writeShort(addr.getPort());
        if (addr.isIpv4()||addr.isIpv6()){
            plain.writeByte(addr.isIpv4()?1:3);
        }else{
            plain.writeByte(2).writeByte(addrBytes.length);
        }
        plain.writeBytes(addrBytes);
        for (int i = 0; i < p; i++) {
            plain.writeByte(ThreadLocalRandom.current().nextInt(0,256));
        }
        //end
        int f=Hash.fnv1a32(plain.nioBuffer(),plain.readableBytes());
        plain.writeInt(f);

        byte[] cmdKey = user.getCmdKey();
        if (!this.session.isAEAD()) {
            byte[] beforeIv = new byte[4*8];
            Unpooled.wrappedBuffer(beforeIv).writerIndex(0).writeLong(timestmap).writeLong(timestmap).writeLong(timestmap).writeLong(timestmap);
            byte[] cmdIv=Hash.md5(beforeIv);
            ByteBufCipher.doFinal(Crypto.getCFBCipher(cmdKey, cmdIv, true), plain, out);
        }else{
            this.aeadCodec.encryptAEADReqHeader(cmdKey,plain,out);
        }
        plain.release();


    }

    private int parseRequestLength(int len){
        if (this.requestMask==null){
            this.requestMask=new Mask(session.getRequestIv());
        }
        return this.requestMask.parse(len);
    }
    private int parseResponseLength(int len){
        if (this.responseMask==null){
            this.responseMask=new Mask(session.getResponseIv());
        }
        return this.responseMask.parse(len);
    }

    private void encodeContent(ByteBuf buf,ByteBuf out) throws Exception {
        if (!buf.isReadable()){
            return;
        }
        int paddingSize = this.requestCrypto.paddingSize();
        int readBytesSize= 16384 - paddingSize;
        readBytesSize=Math.min(readBytesSize,buf.readableBytes());

        ByteBuf plainBuffer = buf.readSlice(readBytesSize);

        int len=readBytesSize+paddingSize;
        if (session.isOptionExists(Option.CHUNK_MASKING)) {
            len=parseRequestLength(len);
        }
        out.writeShort(len);

        this.requestCrypto.encrypt(plainBuffer,out);

        encodeContent(buf, out);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        if (msg instanceof VmessRequest){
            encodeRequest((VmessRequest) msg,out);
        }else {
            if (session.isOptionExists(Option.CHUNK_STREAM)){
                encodeContent((ByteBuf) msg,out);
            }else{
                out.writeBytes((ByteBuf) msg);
            }


        }
    }



    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (state==0){
            //decode header
            if (!this.session.isAEAD()) {
                int expectReadableBytes= this.expectHeaderLength != null ? this.expectHeaderLength : 4;
                if (in.readableBytes()<expectReadableBytes){
                    return;
                }
                int readerIndex = in.readerIndex();
                ByteBuf cipherText = in.readSlice(expectReadableBytes);
                Cipher cipher = Crypto.getCFBCipher(session.getResponseKey(), session.getResponseIv(), false);
                ByteBuf plain = ByteBufCipher.doFinal(cipher, cipherText, ctx.alloc());
                try {
                    if (decodeRespHeader(plain,out)){
                        state = 1;
                    }else{
                        in.readerIndex(readerIndex);
                    }
                } finally {
                    plain.release();
                }
            }else{
                if (this.expectHeaderLength==null){
                    if (in.readableBytes()<18){
                        return;
                    }
                    this.expectHeaderLength=this.aeadCodec.decryptAEADRespHeaderLength(session.getResponseKey(), session.getResponseIv(),in.readSlice(18),ctx.alloc());
                }
                if (in.readableBytes()<this.expectHeaderLength){
                    return;
                }
                ByteBuf plain = this.aeadCodec.decryptAEADRespHeader(session.getResponseKey(), session.getResponseIv(), in.readSlice(this.expectHeaderLength), ctx.alloc());
                try {
                    decodeRespHeader(plain,out);
                    state = 1;
                } finally {
                    plain.release();
                }

            }
        }else{
            //decode content
            if (!session.isOptionExists(Option.CHUNK_STREAM)){
                ByteBuf o = ctx.alloc().buffer(in.readableBytes());
                in.readBytes(o);
                out.add(o);
            }else{
                if (this.expectContentLength==null) {
                    if (in.readableBytes()<2){
                        return;
                    }
                    int length = in.readUnsignedShort();
                    if (session.isOptionExists(Option.CHUNK_MASKING)){
                        length=parseResponseLength(length);
                    }
                    if (length == 0) {
                        out.add(Unpooled.EMPTY_BUFFER);
                        return;
                    }
                    this.expectContentLength=length;
                }
                if (in.readableBytes()<this.expectContentLength){
                    return;
                }

                ByteBuf bytes = in.readSlice(this.expectContentLength);
                ByteBuf decrypt = this.responseCrypto.decrypt(bytes);

                out.add(decrypt);
                this.expectContentLength=null;
                decode(ctx, in, out);
            }

        }

    }

    private boolean decodeRespHeader(ByteBuf plain,List<Object> out){
        var header = plain.readByte();
        if (header != session.getResponseHeader()) {
            throw new RuntimeException("response header not match");
        }
        plain.skipBytes(1);
        var cmd = plain.readByte();
        var cmdLength = plain.readByte();
        if (cmd != 0 && cmdLength != 0) {
            this.expectHeaderLength = (int)cmdLength ;
            if (plain.readableBytes()<this.expectHeaderLength){
                this.expectHeaderLength +=4;
                return false;
            }
            decodeCmd(plain,out);
        }
        return true;
    }

    private boolean decodeCmd(ByteBuf cmdBuffer, List<Object> out) {
        int port=cmdBuffer.readUnsignedShort();
        UUID uuid=new UUID(cmdBuffer.readLong(),cmdBuffer.readLong());
        int alterId=cmdBuffer.readUnsignedShort();
        byte level = cmdBuffer.readByte();
        short min = cmdBuffer.readUnsignedByte();
        out.add(new VmessDynamicPortCmd(port,uuid,alterId,level,min));
        return true;
    }

    public class Mask{
        private SHAKEDigest digest;
        private byte[] maskBuffer=new byte[2];

        public Mask(byte[] iv) {
            this.digest=new SHAKEDigest(128);
            this.digest.update(iv,0,iv.length);
        }

        private int next(){
            this.digest.doOutput(maskBuffer,0,2);
            return Bufs.readUnsignedShort(this.maskBuffer,0);
        }

        public int parse(int delta){
            int mask = next();
            return mask ^ delta;
        }

    }
}
