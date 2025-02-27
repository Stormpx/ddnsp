package io.crowds.tun.wireguard;

import io.crowds.Ddnsp;
import io.crowds.Platform;
import io.crowds.lib.boringtun.wireguard_result;
import io.crowds.util.Async;
import io.crowds.util.Inet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.channel.tun.Tun4Packet;
import org.drasyl.channel.tun.Tun6Packet;
import org.drasyl.channel.tun.TunPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.crowds.lib.boringtun.wireguard_ffi_h.*;

public class WireGuardTunnel implements Closeable {
    private final static Logger logger = LoggerFactory.getLogger(WireGuardTunnel.class);
    private final EventLoop eventLoop;
    private final Function<InternetProtocolFamily,DatagramChannel> channelFactory;
    private boolean creatingSock;
    private DatagramSock sock;
    private List<ByteBuf> pendingList;
    private PeerOption peer;
    private Arena arena;
    private MemorySegment tunnel;

    private SegmentAllocator resultAllocator;
    private ScheduledFuture<?> timerFuture;
    private MemorySegment outBuffer;

    private Consumer<TunPacket> packetHandler;

    public WireGuardTunnel(EventLoop eventLoop, Function<InternetProtocolFamily, DatagramChannel> channelFactory, String privateKey, PeerOption peer, int index) {
        this.eventLoop=eventLoop;
        this.channelFactory = channelFactory;
        this.peer=peer;
        try (Arena session = Arena.ofConfined()){
            this.tunnel = new_tunnel(
                    session.allocateArray(C_CHAR,privateKey.getBytes(StandardCharsets.US_ASCII)),
                    session.allocateArray(C_CHAR,peer.publicKey().getBytes(StandardCharsets.US_ASCII)),
                    session.allocateArray(C_CHAR,peer.perSharedKey().getBytes(StandardCharsets.US_ASCII)),
                    peer.keepAlive(),
                    index
            );
            if (this.tunnel.address()==0){
                throw new RuntimeException("create wireGuard tunnel failed");
            }
        }
        this.arena =Arena.ofShared();
        this.outBuffer = this.arena.allocate(65536);
        this.resultAllocator = SegmentAllocator.prefixAllocator(arena.allocate(wireguard_result.$LAYOUT()));
        this.pendingList=new ArrayList<>();
        this.timerFuture = eventLoop.scheduleAtFixedRate(this::oneTick, 250, 250,TimeUnit.MILLISECONDS);
        this.allocChannel(peer.endpointAddr());
    }

    record DatagramSock(DatagramChannel channel, InetSocketAddress remote){

        public void write(ByteBuf content){
            channel.writeAndFlush(new DatagramPacket(content,remote));
        }

        public void write(List<ByteBuf> contents){
            for (ByteBuf content : contents) {
                channel.write(new DatagramPacket(content,remote));
            }
            channel.flush();
        }

        public void close(){
            channel.close();
        }
    }

    private void allocChannel(InetSocketAddress remoteAddress) {
        if (remoteAddress.isUnresolved()){
            Async.toCallback(eventLoop,Ddnsp.dnsResolver().resolve(remoteAddress.getHostString(),null))
                    .addListener(f->{
                        if (f.isSuccess()){
                            allocChannel(Inet.createSocketAddress((String) f.resultNow(), remoteAddress.getPort()));
                        }else{
                            creatingSock=false;
                        }
                    });
            creatingSock=true;
            return;
        }
        if (creatingSock){
            return;
        }
        boolean ipv4 = remoteAddress.getAddress() instanceof Inet4Address;
        var local = new InetSocketAddress(0);
        DatagramChannel channel = channelFactory.apply(ipv4? InternetProtocolFamily.IPv4:InternetProtocolFamily.IPv6);
        eventLoop.register(channel).addListener(bf->{
            if (!bf.isSuccess()){
                creatingSock=false;
                return;
            }
            channel.bind(local).addListener(f->{
                creatingSock=false;
                if (!f.isSuccess()){
                    logger.error("bind port failed.",f.cause());
                    channel.close();
                }else{
                    channel.pipeline().addLast(new WgInternalHandler());
                    this.sock = new DatagramSock(channel, remoteAddress);
                    this.sock.write(pendingList);
                    this.pendingList.clear();
                }
            });
        });
        creatingSock=true;
    }

    private void writeToNetwork(ByteBuf content){
        if (this.sock==null){
            if (!creatingSock){
                allocChannel(peer.endpointAddr());
            }
            this.pendingList.add(content);
            return;
        }
        this.sock.write(content);
    }

    private boolean isWriteToNetWork(MemorySegment result){
        int op = wireguard_result.op$get(result);
        return op==1;
    }

    private void handleConnectionExpired(){
        if (this.sock!=null){
            this.sock.close();
            this.sock=null;
        }
    }

    private void handleWireGuardResult(MemorySegment result,MemorySegment dst){
        int op = wireguard_result.op$get(result);
        if (op==0)
            return;
        int size = (int)wireguard_result.size$get(result);
        if (op==2){
            WireGuardError error = WireGuardError.valueOf(size);
            if (error==WireGuardError.ConnectionExpired){
                handleConnectionExpired();
            }else{
                logger.error("wireGuard tunnel {} error occur",error);
            }
            return;
        }

        if (size>0){
            ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(size);
            buf.writeBytes(dst.asSlice(0,size).asByteBuffer());
            switch (op){
                case 1-> writeToNetwork(buf);
                case 4->packetHandler.accept(new Tun4Packet(buf));
                case 6->packetHandler.accept(new Tun6Packet(buf));
            }
        }
    }


    private void oneTick(){
        if (tunnel==null){
            return;
        }
        var result = wireguard_tick(
                resultAllocator,
                tunnel,
                outBuffer, (int) outBuffer.byteSize());
        handleWireGuardResult(result,outBuffer);
    }

    public void write(ByteBuf src){
        if (eventLoop.inEventLoop()){
            MemorySegment in = MemorySegment.ofBuffer(src.nioBuffer());
            try {
                var result = wireguard_write(
                        resultAllocator,
                        tunnel,
                        in, (int)in.byteSize(),
                        outBuffer, (int) outBuffer.byteSize());
                handleWireGuardResult(result,outBuffer);
            }finally {
                ReferenceCountUtil.safeRelease(src);
            }
        }else{
            eventLoop.execute(()-> write(src));
        }


    }

    public WireGuardTunnel packetHandler(Consumer<TunPacket> packetHandler) {
        this.packetHandler = packetHandler;
        return this;
    }

    public PeerOption peer(){
        return peer;
    }
    public boolean match(InetAddress address){
        return peer.allowedIp().isMatch(address.getAddress());
    }

    public int mask(){
        return peer.allowedIp().getMask();
    }

    @Override
    public void close() throws IOException {
        if (this.timerFuture!=null){
            this.timerFuture.cancel(false);
        }
        if (this.tunnel!=null){
            tunnel_free(this.tunnel);
        }

        arena.close();
    }

    class WgInternalHandler extends ChannelDuplexHandler {


        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!(msg instanceof DatagramPacket packet)) {
                super.channelRead(ctx,msg);
            } else {
                ByteBuf buf = packet.content();

                try {
                    MemorySegment in = MemorySegment.ofBuffer(buf.nioBuffer());
                    var result = wireguard_read(resultAllocator,
                            tunnel,
                            in, (int)in.byteSize(),
                            outBuffer, (int) outBuffer.byteSize());
                    handleWireGuardResult(result,outBuffer);
                    while (isWriteToNetWork(result)){
                        result = wireguard_read(resultAllocator,
                                tunnel,
                                in, 0,
                                outBuffer, (int) outBuffer.byteSize());
                        handleWireGuardResult(result,outBuffer);
                    }
                } finally {
                    ReferenceCountUtil.release(packet);
                }
            }
        }
    }

}
