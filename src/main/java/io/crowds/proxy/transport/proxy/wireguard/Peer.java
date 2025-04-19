package io.crowds.proxy.transport.proxy.wireguard;

import io.crowds.compoments.wireguard.PeerOption;
import io.crowds.compoments.wireguard.WireGuardTunnel;
import io.crowds.compoments.wireguard.WireguardErrorException;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.Transport;
import io.crowds.util.AddrType;
import io.crowds.util.Pkts;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stormpx.net.buffer.ByteArray;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class Peer {

    private static final Logger logger = LoggerFactory.getLogger(Peer.class);
    private final EventLoop eventLoop;
    private final String privateKey;
    private final PeerOption option;
    private final Destination destination;
    private final MpscArrayQueue<ByteArray> packetQueue;

    private volatile Transport transport;
    private final AtomicBoolean channelOpening;
    private volatile DatagramChannel outgoingChannel;
    private volatile Consumer<ByteArray> networkPacketHandler;

    public Peer(EventLoop eventLoop,String privateKey,PeerOption option) {
        this.eventLoop =eventLoop;
        this.privateKey = privateKey;
        this.option = option;
        this.destination = new Destination(NetAddr.of(option.endpoint()), TP.UDP);
        this.packetQueue = new MpscArrayQueue<>(1024);
        this.channelOpening = new AtomicBoolean(false);
    }

    private void handleNetworkPacket(ByteArray byteArray){
        Consumer<ByteArray> networkPacketHandler = this.networkPacketHandler;
        if (networkPacketHandler!=null){
            networkPacketHandler.accept(byteArray);
        }
    }

    private void handleTunnelException(IOException e){
        if (e==null){
            return;
        }

        if (e instanceof WireguardErrorException we){
            logger.warn("wireguard peer:{} error occur:",we.getError());
            DatagramChannel outgoingChannel = this.outgoingChannel;
            if (outgoingChannel!=null){
                outgoingChannel.close();
                this.outgoingChannel=null;
            }
        }
    }

    private void drainPacketToChannel(DatagramChannel channel){
        boolean flush =false;
        while (!packetQueue.isEmpty()){
            ByteArray array = packetQueue.poll();
            if (array!=null) {
                channel.write(array);
                flush = true;
            }
        }
        if (flush) {
            channel.flush();
        }
    }

    private void createChannel(){
        try {
            if (!channelOpening.compareAndSet(false,true)){
                return;
            }
            logger.info("{} {}",destination,AddrType.of(option.endpoint()));
            transport.openChannel(eventLoop, destination, AddrType.of(option.endpoint()))
                     .addListener(f->{
                         try {
                             if (!f.isSuccess()){
                                 logger.error("Create wireguard outgoing channel for peer {} failed: {}",option.endpoint(),f.cause().getMessage());
                                 if (logger.isDebugEnabled()){
                                     logger.error("",f.cause());
                                 }
                                 this.packetQueue.clear();
                                 return;
                             }

                             DatagramChannel outgoingChannel = (DatagramChannel) f.get();
                             try {
                                 TunnelHandler tunnelHandler = new TunnelHandler();
                                 outgoingChannel.pipeline().addLast(tunnelHandler);
                                 tunnelHandler.wireGuardTunnel.closeHandler(this::handleTunnelException);
                                 this.outgoingChannel = outgoingChannel;
                                 drainPacketToChannel(outgoingChannel);
                             } catch (Exception e) {
                                 logger.error("",e);
                                 outgoingChannel.close();
                             }
                         } finally {
                             channelOpening.compareAndSet(true,false);
                         }
                     });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Peer setTransport(Transport transport) {
        this.transport = transport;
        return this;
    }
    public Peer networkPacketHandler(Consumer<ByteArray> networkPacketHandler) {
        this.networkPacketHandler = networkPacketHandler;
        return this;
    }

    public boolean match(InetAddress address){
        return option.allowedIp().isMatch(address.getAddress());
    }

    public int mask(){
        return option.allowedIp().getMask();
    }

    public void write(ByteArray packet){

        final DatagramChannel outgoingChannel = this.outgoingChannel;
        if (outgoingChannel !=null){
            outgoingChannel.writeAndFlush(packet);
        }else {
            this.packetQueue.offer(packet);
            createChannel();
        }
    }
    private class TunnelHandler extends ChannelDuplexHandler{
        private final WireGuardTunnel wireGuardTunnel;

        TunnelHandler(){
            this.wireGuardTunnel = new WireGuardTunnel(eventLoop,privateKey,option);
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            this.wireGuardTunnel.packetReadHandler(bb->{
                ByteArray byteArray = ByteArray.alloc(bb.limit());
                byteArray.setBuffer(0,ByteArray.wrap(bb),0,bb.limit());
                handleNetworkPacket(byteArray);
            }).packetWriteHandler(bb->{
                ByteBuf byteBuf = ctx.channel().alloc().ioBuffer(bb.limit());
                byteBuf.writeBytes(bb);
                ctx.writeAndFlush(new DatagramPacket(byteBuf,option.endpoint()));
            });
            super.handlerAdded(ctx);
        }


        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try {
                if (msg instanceof DatagramPacket packet){
                    ByteBuf byteBuf = packet.content();
                    this.wireGuardTunnel.readPacket(byteBuf.nioBuffer(0,byteBuf.readableBytes()));
                }
            } catch (Exception e) {
                logger.error("",e);
                throw e;
            }finally {
                ReferenceCountUtil.safeRelease(msg);
            }
        }


        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof ByteArray buf){
                this.wireGuardTunnel.writePacket(Pkts.toDirectBuffer(buf));
                promise.setSuccess();
            }else if (msg instanceof ByteBuf buf){
                if (!buf.isDirect()){
                    ByteBuf byteBuf = ctx.channel().alloc().directBuffer(buf.readableBytes());
                    byteBuf.writeBytes(buf);
                    ReferenceCountUtil.safeRelease(buf);
                    buf = byteBuf;
                }
                this.wireGuardTunnel.writePacket(buf.nioBuffer(0,buf.readableBytes()));
                ReferenceCountUtil.safeRelease(buf);
                promise.setSuccess();
            }else{
                ReferenceCountUtil.safeRelease(msg);
                promise.setFailure(new RuntimeException("Unrecognized message type for wireguard"));
            }
        }
    }

}
