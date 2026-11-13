package io.crowds.proxy.transport.proxy.xudp;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.TP;
import io.crowds.util.Inet;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.ReplayingDecoder;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.crowds.proxy.transport.proxy.xudp.Mux.*;

/**
 * Mux.Cool frame codec for the XUDP usage: encodes and decodes {@link Mux} frames over a
 * websocket/stream connection.
 */
public class MuxCodec extends CombinedChannelDuplexHandler<MuxCodec.Decoder, MuxCodec.Encoder> {

    public MuxCodec() {
        init(new Decoder(), new Encoder());
    }

    public static class Encoder extends MessageToByteEncoder<Mux> {

        private byte[] hostBytes(NetAddr addr){
            String host = addr.getHost();
            byte[] bytes = host.getBytes(StandardCharsets.US_ASCII);
            if (bytes.length > MAX_DOMAIN_LEN){
                throw new IllegalArgumentException("destination domain too long: " + host + " (" + bytes.length + " > " + MAX_DOMAIN_LEN + ")");
            }
            return bytes;
        }

        private void encodeAddr(NetAddr addr, ByteBuf out){
            //note: mux puts the port first and the ATYP after it, the opposite of socks5
            out.writeShort(addr.getPort());
            if (addr.isIpv4()){
                out.writeByte(ADDR_TYPE_IPV4);
                out.writeBytes(addr.getByte());
            }else if (addr.isIpv6()){
                out.writeByte(ADDR_TYPE_IPV6);
                out.writeBytes(addr.getByte());
            }else{
                byte[] host = hostBytes(addr);
                out.writeByte(ADDR_TYPE_DOMAIN);
                out.writeByte(host.length);
                out.writeBytes(host);
            }
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, Mux msg, ByteBuf out) throws Exception {
            int type = Mux.type(msg);
            int metaLenIdx = out.writerIndex();
            out.writeShort(0);
            out.writeShort(msg.id());
            out.writeByte(type);
            ByteBuf data = msg.data();
            out.writeByte((data==null ? 0x00 : OPTION_DATA) | (msg.error() ? OPTION_ERROR : 0x00));
            switch (msg){
                case Mux.New n -> {
                    out.writeByte(n.network() == TP.TCP ? NETWORK_TCP : NETWORK_UDP);
                    encodeAddr(n.addr(),out);
                    if (n.network() == TP.UDP) {
                        if (n.globalId()==null || n.globalId().length != GLOBAL_ID_LEN){
                            throw new IllegalArgumentException("invalid xudp global id, expect "+GLOBAL_ID_LEN+" bytes");
                        }
                        out.writeBytes(n.globalId());
                    }
                }
                case Mux.Keep keep -> {
                    NetAddr addr = keep.addr();
                    if (addr != null) {
                        //target override: only meaningful for UDP, network marker followed by the address
                        out.writeByte(NETWORK_UDP);
                        encodeAddr(addr,out);
                    }
                }
                case Mux.End _, Mux.KeepAlive _ -> {

                }
            }
            int metaLen = out.writerIndex() - (metaLenIdx + 2);
            int maxMetaLen = Mux.maxLen(type);
            if (metaLen > maxMetaLen){
                throw new IllegalArgumentException("mux "+Mux.statusName(type)+" meta too long: " + metaLen + " > " + maxMetaLen);
            }
            out.setShort(metaLenIdx,metaLen);
            if (data!=null){
                out.writeShort(data.readableBytes());
                out.writeBytes(data);
            }
        }
    }


    public static class Decoder extends ReplayingDecoder<Decoder.State> {

        enum State{
            META,
            DATA
        }

        public Decoder() {
            super(State.META);
        }

        private int id;
        private int type;
        private boolean error;
        private TP network;
        private NetAddr addr;
        private byte[] globalId;

        private void reset(){
            this.id = 0;
            this.type = 0;
            this.error = false;
            this.network = null;
            this.addr = null;
            this.globalId = null;
        }

        private DecoderException malformed(String format, Object... args){
            return new DecoderException("[mux] " + String.format(format, args));
        }

        private NetAddr decodeAddr(ByteBuf in){
            //note: mux puts the port first and the ATYP after it, the opposite of socks5
            int port = in.readUnsignedShort();
            int aType = in.readUnsignedByte();
            return switch (aType){
                case ADDR_TYPE_IPV4 -> {
                    byte[] raw = new byte[4];
                    in.readBytes(raw);
                    yield NetAddr.of(new InetSocketAddress(Inet.address(raw),port));
                }
                case ADDR_TYPE_DOMAIN -> {
                    int len = in.readUnsignedByte();
                    if (len == 0){
                        throw malformed("empty domain");
                    }
                    byte[] raw = new byte[len];
                    in.readBytes(raw);
                    yield NetAddr.of(new String(raw, StandardCharsets.US_ASCII),port);
                }
                case ADDR_TYPE_IPV6 -> {
                    byte[] raw = new byte[16];
                    in.readBytes(raw);
                    yield NetAddr.of(new InetSocketAddress(Inet.address(raw),port));
                }
                default -> throw malformed("unknown address type: %d (port=%d)",aType,port);
            };
        }

        private Mux emitMux(ByteBuf data){
            var mux =  switch (type){
                case TYPE_NEW -> new Mux.New(id,network,addr,globalId,data);
                case TYPE_KEEP -> new Mux.Keep(id,network,addr,data);
                case TYPE_END -> new Mux.End(id,error,data);
                case TYPE_KEEP_ALIVE -> new Mux.KeepAlive(id,data);
                default -> throw malformed("unknown status: %d",type);
            };
            reset();
            return mux;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {

            switch (state()){
                case META -> {
                    int metaLen = in.readUnsignedShort();
                    if (metaLen < MIN_META_LEN || metaLen > MAX_META_LEN){
                        throw malformed("invalid meta length: %d, expect [%d, %d]",metaLen,MIN_META_LEN,MAX_META_LEN);
                    }
                    ByteBuf meta = in.readSlice(metaLen);
                    int id = meta.readUnsignedShort();
                    int type = meta.readUnsignedByte();
                    int option = meta.readUnsignedByte();
                    boolean hasData = (option & OPTION_DATA) != 0;
                    boolean error = (option & OPTION_ERROR) != 0;

                    TP network = null;
                    NetAddr addr = null;
                    byte[] globalId = null;

                    switch (type){
                        case TYPE_NEW -> {
                            int net = meta.readUnsignedByte();
                            network = switch (net){
                                case NETWORK_TCP -> TP.TCP;
                                case NETWORK_UDP -> TP.UDP;
                                default -> throw malformed("unknown network type: %d",net);
                            };
                            addr = decodeAddr(meta);
                            //the GlobalID is appended at the end of meta and only exists when
                            //"data bit set + UDP + at least 8 bytes left"; anything after it is not defined
                            //by the spec at this position, so only the first 8 bytes are taken
                            if (hasData && network == TP.UDP && meta.readableBytes() >= GLOBAL_ID_LEN){
                                globalId = new byte[GLOBAL_ID_LEN];
                                meta.readBytes(globalId);
                            }
                        }
                        case TYPE_KEEP -> {
                            //target override: only present when meta still has content and the byte read
                            //here is network=UDP. This marker has to be checked before parsing the address,
                            //otherwise a plain Keep with metaLen=4 would consume it as a target and shift
                            if (meta.isReadable() && meta.readByte() == NETWORK_UDP){
                                network = TP.UDP;
                                addr = decodeAddr(meta);
                            }
                        }
                        case TYPE_END, TYPE_KEEP_ALIVE -> {
                            if (metaLen != MIN_META_LEN){
                                throw malformed("unexpected meta length for %s: %d",Mux.statusName(type),metaLen);
                            }
                        }
                        default -> throw malformed("unknown status: %d",type);
                    }

                    if (meta.isReadable()){
                        //defensive: ignore the trailing bytes a status does not define
                        meta.skipBytes(meta.readableBytes());
                    }

                    this.id = id;
                    this.type = type;
                    this.error = error;
                    this.network = network;
                    this.addr = addr;
                    this.globalId = globalId;

                    if (hasData){
                        checkpoint(State.DATA);
                    }else{
                        out.add(emitMux(null));
                    }
                }
                case DATA -> {
                    int dataLen = in.readUnsignedShort();
                    ByteBuf data = in.readBytes(dataLen);
                    out.add(emitMux(data));
                    checkpoint(State.META);
                }
            }

        }
    }



}
