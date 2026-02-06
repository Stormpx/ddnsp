package io.crowds.proxy.transport.proxy.tuic.udp;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.common.BaseChannelInitializer;
import io.crowds.proxy.transport.proxy.tuic.TuicCommand;
import io.crowds.proxy.transport.proxy.tuic.TuicConnection;
import io.crowds.util.Async;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class UdpSessions {

    private static final Logger logger = LoggerFactory.getLogger(UdpSessions.class);
    private final TuicConnection connection;
    private final EventLoop eventLoop;
    private final AtomicLong associateIdCounter = new AtomicLong(0);
    private final Future<TuicLocalServerChannel> localServerFuture;
    private TuicLocalServerChannel tuicLocalServer;
    private final Map<Integer,TuicLocalChannel> sessions = new HashMap<>();

    public UdpSessions(TuicConnection connection) {
        this.connection = connection;
        this.eventLoop = connection.eventLoop();

        var cf = new ServerBootstrap()
                .group(eventLoop)
                .channelFactory(TuicLocalServerChannel::new)
                .childHandler(new ChannelInitializer<TuicLocalChannel>() {
                    @Override
                    protected void initChannel(TuicLocalChannel ch) throws Exception {
                        ch.pipeline().addLast(new DatagramHandler(ch.associateId));
                    }
                })
                .bind(new LocalAddress(UUID.randomUUID().toString()));
        Promise<TuicLocalServerChannel> promise = eventLoop.newPromise();
        cf.addListener(f->{
            assert f.isSuccess();
            TuicLocalServerChannel channel = (TuicLocalServerChannel) cf.channel();
            this.tuicLocalServer = channel;
            promise.setSuccess(channel);
        });
        this.localServerFuture = promise;
    }



    private int nextAssociateId(){
        return Math.toIntExact(this.associateIdCounter.getAndIncrement() & 65535);
    }

    public Future<Channel> associate(){
        Promise<Channel> promise = eventLoop.newPromise();

        Async.cascadeFailure(this.localServerFuture,promise,f->{
            TuicLocalServerChannel serverChannel = f.get();
            int associateId = nextAssociateId();
            var cf = new Bootstrap()
                    .localAddress(new LocalAddress(UUID.randomUUID().toString()))
                    .group(eventLoop)
                    .channelFactory(()->new TuicLocalChannel(associateId))
                    .handler(BaseChannelInitializer.EMPTY)
                    .connect(serverChannel.localAddress());
            Async.cascadeFailure(cf,promise,_->promise.setSuccess(cf.channel()));
        });

        return promise;
    }


    public long getAssociateCount(){
        return sessions.size();
    }

    public void recvPacket(TuicCommand.Packet packet){
        int assocId = packet.assocId();
        TuicLocalChannel tuicLocalChannel = sessions.get(assocId);
        if (tuicLocalChannel!=null){
            tuicLocalChannel.writeAndFlush(packet);
        }else{
            ReferenceCountUtil.safeRelease(packet.data());
        }
    }

    public void close(){
        for (TuicLocalChannel localChannel : List.copyOf(this.sessions.values())) {
            localChannel.close();
        }
        if (tuicLocalServer!=null){
            tuicLocalServer.close();
        }
    }

    private final class DatagramHandler extends ChannelDuplexHandler{

        private final int associateId;
        private final PacketAssembler packetAssembler;
        private long pktId = 0;
        private boolean closed;

        private DatagramHandler(int associateId) {
            this.associateId = associateId;
            this.packetAssembler = new PacketAssembler(UdpSessions.this.eventLoop);
        }

        private int nextPktId(){
            return Math.toIntExact(pktId++ & 65535);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (closed){
                ReferenceCountUtil.safeRelease(msg);
                return;
            }
            if (!(msg instanceof DatagramPacket packet)){
                super.channelRead(ctx, msg);
                return;
            }
            TuicConnection connection = UdpSessions.this.connection;
            var addr = NetAddr.of(packet.recipient());
            ByteBuf data = packet.content();
            int pktId = nextPktId();
            connection.sendUdpCommand(new TuicCommand.Packet(associateId,pktId, (short) 1, (short) 0,addr,data));

        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            UdpSessions.this.connection.sendUdpCommand(new TuicCommand.Dissociate(associateId));
            closed = true;
            super.channelInactive(ctx);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            packetAssembler.recycleAll();
            super.handlerRemoved(ctx);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (!(msg instanceof TuicCommand)){
                super.write(ctx, msg, promise);
            }
            if (!(msg instanceof TuicCommand.Packet packet)){
                logger.error("Unexpected tuic command received: {}", msg);
                return;
            }

            Packet udpPacket = packetAssembler.assemble(packet, ctx.alloc());
            if (udpPacket == null) {
                promise.setSuccess();
                return;
            }
            ctx.write(new DatagramPacket(udpPacket.data(), null,udpPacket.addr().getAsInetAddr()),promise);
        }
    }



    private final class TuicLocalServerChannel extends LocalServerChannel {

        @Override
        protected LocalChannel newLocalChannel(LocalChannel peer) {
            assert peer instanceof TuicLocalChannel;
            int associateId = ((TuicLocalChannel) peer).associateId;
            return new TuicLocalChannel(this,peer,associateId);
        }
    }

    private final class TuicLocalChannel extends LocalChannel {
        private final int associateId;
        private final boolean serverSide;


        public TuicLocalChannel(int associateId) {
            this.associateId = associateId;
            this.serverSide = false;
        }

        public TuicLocalChannel(LocalServerChannel parent, LocalChannel peer, int associateId) {
            super(parent, peer);
            this.associateId = associateId;
            this.serverSide = true;
        }

        @Override
        protected void doRegister(ChannelPromise promise) {
            if (serverSide){
                sessions.put(associateId,this);
            }
            super.doRegister(promise);
        }

        @Override
        protected void doClose() throws Exception {
            if (serverSide){
                sessions.remove(associateId,this);
            }
            super.doClose();
        }
    }



}
