package io.crowds.proxy.transport.vmess;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.vmess.crypto.*;
import io.crowds.util.Bufs;
import io.crowds.util.Crypto;
import io.crowds.util.Hash;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import org.bouncycastle.crypto.digests.SHAKEDigest;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class VmessMessageCodec extends ByteToMessageCodec<Object> {



    private VmessSession session;

    private VmessCrypto requestCrypto;
    private VmessCrypto responseCrypto;

    private int state;
    private Integer expectCmdLength;
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


    private void encodeRequest(VmessRequest request,ByteBuf out) throws NoSuchPaddingException, ShortBufferException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        VmessUser user = request.getUser();
        UUID uuid = user.getUuid();

        long timestmap = Instant.now().getEpochSecond();
        this.session=VmessSession.create(user,timestmap);
        this.session.setOpts(request.getOpts());
        createCrypto(request.getSecurity());

        byte[] auth= Hash.hmac(
                ByteBufUtil.getBytes(Unpooled.buffer(8).writeLong(timestmap),0,8,false),
                ByteBufUtil.getBytes(Unpooled.buffer(16).writeLong(uuid.getMostSignificantBits()).writeLong(uuid.getLeastSignificantBits()), 0,16,false),
                "HmacMD5"
        );
        out.writeBytes(auth);

        byte[] cmdKey = user.getCmdKey();
        byte[] beforeIv = new byte[4*8];
        Unpooled.wrappedBuffer(beforeIv).writerIndex(0).writeLong(timestmap).writeLong(timestmap).writeLong(timestmap).writeLong(timestmap);
        byte[] cmdIv=Hash.md5(beforeIv);


        int writerIndex = out.writerIndex();
        out.markWriterIndex();

        out.writeByte(1);

        out.writeBytes(session.getRequestIv())
                .writeBytes(session.getRequestKey())
                .writeByte(session.getResponseHeader());


        encodeOptions(request.getOpts(),out);

        int p = (ThreadLocalRandom.current().nextInt()&0b1111);
        out.writeByte((p<<4)|request.getSecurity().getValue());
        out.writeByte(0);
        out.writeByte(request.getCmd()== TP.TCP?1:2);

        NetAddr addr = request.getAddr();
        byte[] addrBytes = addr.getByte();
        out.writeShort(addr.getPort());
        if (addr.isIpv4()||addr.isIpv6()){
            out.writeByte(addr.isIpv4()?1:3);
        }else{
            out.writeByte(2).writeByte(addrBytes.length);
        }
        out.writeBytes(addrBytes);

        for (int i = 0; i < p; i++) {
            out.writeByte(ThreadLocalRandom.current().nextInt(0,256));
        }
        out.writeInt(0);
        byte[] cmdBytes = ByteBufUtil.getBytes(out, writerIndex, out.writerIndex() - writerIndex);
        int f=Hash.fnv1a32(cmdBytes,cmdBytes.length-4);
        Bufs.writeInt(cmdBytes,cmdBytes.length-4,f);
        byte[] encryptBytes = Crypto.aes128CFBEncrypt(cmdKey, cmdIv, cmdBytes);

        out.resetWriterIndex();
        out.writeBytes(encryptBytes);
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
        int readBytesSize= this.requestCrypto.getMaxSize();
        readBytesSize=Math.min(readBytesSize,buf.readableBytes());
        byte[] bytes = new byte[readBytesSize];
        buf.readBytes(bytes);
        out.markWriterIndex();
        out.writeShort(0);
        this.requestCrypto.encrypt(bytes,out);
        int writerIndex = out.writerIndex();
        out.resetWriterIndex();
        int len = writerIndex - out.writerIndex() - 2;
        if (session.isOptionExists(Option.CHUNK_MASKING)) {
            len=parseRequestLength(len);
        }
        out.writeShort(len)
                .writerIndex(writerIndex);



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
            if (this.expectCmdLength==null) {
                if (in.readableBytes()<4){
                    return;
                }

                byte[] bytes=ByteBufUtil.getBytes(in,0,4);
                byte[] decryptBytes = Crypto.aes128CFBDecrypt(Hash.md5(session.getRequestKey()), Hash.md5(session.getRequestIv()), bytes);
                if (decryptBytes[0]!=session.getResponseHeader()){
                    throw new RuntimeException("response header not match");
                }
                byte cmd = decryptBytes[2];
                int cmdLength = decryptBytes[3];
                if (cmd != 0&&cmdLength!=0) {
                    this.expectCmdLength = cmdLength+4;
                } else {
                    in.skipBytes(4);
                    state = 1;

                }
                decode(ctx, in, out);
            }else{
                if (decodeCmd(in,out)) {
                    state = 1;
                    decode(ctx, in, out);
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

                byte[] bytes = new byte[this.expectContentLength];
                in.readBytes(bytes);
                byte[] decrypt = this.responseCrypto.decrypt(bytes);

                out.add(Unpooled.wrappedBuffer(decrypt));
                this.expectContentLength=null;
                decode(ctx, in, out);
            }

        }

    }

    public boolean decodeCmd(ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes()<this.expectCmdLength){
            return false;
        }
        byte[] bytes = new byte[this.expectCmdLength];
        in.readBytes(bytes);
        byte[] decryptBytes = Crypto.aes128CFBDecrypt(session.getRequestKey(), session.getRequestIv(), bytes);
        ByteBuf cmdBuffer = Unpooled.wrappedBuffer(decryptBytes);
        cmdBuffer.skipBytes(4);
        cmdBuffer.skipBytes(1);
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
