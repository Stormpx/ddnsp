package io.crowds.proxy.transport.proxy.tuic;

import io.crowds.proxy.NetAddr;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.ReplayingDecoder;

import java.util.List;
import java.util.UUID;

public class TuicCodec extends CombinedChannelDuplexHandler<TuicCodec.Decoder, TuicCodec.Encoder> {
    private final byte version;

    public TuicCodec() {
        this((byte) 0x05);
    }

    public TuicCodec(byte version) {
        this.version = version;
        this.init(new Decoder(),new Encoder());
    }


    public class Encoder extends MessageToByteEncoder<TuicCommand>{


        private void encodeFragment(int assocId, int pktId, short fragTotal, short fragId, NetAddr addr,ByteBuf data,ByteBuf out){
            out.writeByte(0x02);
            out.writeShort(assocId);
            out.writeShort(pktId);
            out.writeByte(fragTotal);
            out.writeByte(fragId);
            if (data.readableBytes()>65535){
                throw new RuntimeException("Data size too large");
            }
            out.writeShort(data.readableBytes());
            Tuic.encodeAddr(addr,out);
            out.writeBytes(data);
        }


        @Override
        protected void encode(ChannelHandlerContext channelHandlerContext, TuicCommand cmd, ByteBuf out) throws Exception {
            out.writeByte(version);
            switch (cmd){
                case TuicCommand.Auth(UUID uuid, byte[] token) -> {
                    out.writeByte(0x00);
                    out.writeLong(uuid.getMostSignificantBits()).writeLong(uuid.getLeastSignificantBits());
                    out.writeBytes(token);
                }
                case TuicCommand.Connect(NetAddr addr) -> {
                    out.writeByte(0x01);
                    Tuic.encodeAddr(addr,out);
                    TuicCodec.this.inboundHandler().state(Decoder.State.Relaying);
                }
                case TuicCommand.Packet(int assocId, int pktId, short fragTotal, short fragId, NetAddr addr, ByteBuf data) ->
                        encodeFragment(assocId,pktId,fragTotal,fragId,addr,data,out);
                case TuicCommand.Dissociate(int assocId) -> {
                    out.writeByte(0x03);
                    out.writeShort(assocId);
                }
                case TuicCommand.Heartbeat heartbeat -> out.writeByte(0x04);
            }
        }
    }

    public class Decoder extends ReplayingDecoder<Decoder.State> {

        public Decoder() {
            super(State.None);
        }

        enum State{
            None,
            Command,
            Relaying,
            Packet
        }
        record Packet(int assocId, int pktId, short fragTotal, short fragId, int size, NetAddr addr){}

        private int type;

        private Packet packet;

        @Override
        protected State state(State newState) {
            return super.state(newState);
        }

        @Override
        protected State state() {
            return super.state();
        }

        @Override
        protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf in, List<Object> out) throws Exception {
            var state = state();
            switch (state){
                case None -> {
                    int version = in.readUnsignedByte();
                    if (version!=TuicCodec.this.version){
                        throw new RuntimeException("Invalid version: "+version);
                    }
                    this.type = in.readUnsignedByte();
                    state(State.Command);
                }
                case Command -> {
                    int type = this.type;
                    if (type==0x0){
                        long mostSigBits = in.readLong();
                        long leastSigBits = in.readLong();
                        ByteBuf token = in.readBytes(32);
                        byte[] bytes = new byte[32];
                        token.readBytes(bytes);
                        out.add(new TuicCommand.Auth(new UUID(mostSigBits,leastSigBits),bytes));
                        state(null);
                    }else if (type==0x1){
                        NetAddr addr = Tuic.decodeAddr(in);
                        out.add(new TuicCommand.Connect(addr));
                        state(State.Relaying);
                    }else if (type==0x2){
                        int assocId = in.readUnsignedShort();
                        int pktId = in.readUnsignedShort();
                        short fragTotal = in.readUnsignedByte();
                        short fragId = in.readUnsignedByte();
                        int size = in.readUnsignedShort();
                        NetAddr addr = Tuic.decodeAddr(in);
                        if (size==0){
                            out.add(new TuicCommand.Packet(assocId, pktId, fragTotal, fragId, addr, Unpooled.EMPTY_BUFFER));
                            state(State.None);
                        }else{
                            this.packet = new Packet(assocId,pktId,fragTotal,fragId,size,addr);
                            state(State.Packet);
                        }

                    }else if (type==0x3){
                        out.add(new TuicCommand.Dissociate(in.readUnsignedShort()));
                        state(State.None);
                    }else if (type==0x4){
                        out.add(new TuicCommand.Heartbeat());
                        state(State.None);
                    }else{
                        throw new RuntimeException("Invalid command type:"+state);
                    }
                }
                case Relaying -> out.add(in.readBytes(actualReadableBytes()));
                case Packet -> {
                    var packet = this.packet;
                    assert packet != null;
                    ByteBuf data = in.readBytes(packet.size);
                    out.add(new TuicCommand.Packet(packet.assocId, packet.pktId, packet.fragTotal, packet.fragId, packet.addr, data));
                    this.packet = null;
                    state(State.None);
                }
            }

        }
    }

}
