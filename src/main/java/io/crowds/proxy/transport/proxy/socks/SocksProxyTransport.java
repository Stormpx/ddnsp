package io.crowds.proxy.transport.proxy.socks;

import io.crowds.proxy.*;
import io.crowds.proxy.common.IdleTimeoutHandler;
import io.crowds.proxy.common.Socks;
import io.crowds.proxy.transport.Destination;
import io.crowds.proxy.transport.proxy.FullConeProxyTransport;
import io.crowds.util.AddrType;
import io.crowds.util.Async;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.internal.ChannelUtils;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.InetSocketAddress;
import java.nio.channels.Channels;

public class SocksProxyTransport extends FullConeProxyTransport {
    private SocksOption socksOption;

    private final Destination destination;

    public SocksProxyTransport(ChannelCreator channelCreator, SocksOption socksOption) {
        super(channelCreator, socksOption);
        this.socksOption=socksOption;
        this.destination=new Destination(NetAddr.of(socksOption.getRemote()),TP.TCP);
    }

    @Override
    public String getTag() {
        return socksOption.getName();
    }

    @Override
    protected Destination getRemote(TP tp) {
        return destination;
    }

    private void safeClose(Channel channel){
        if (channel.isActive()){
            channel.close();
        }
    }

    @Override
    protected Future<Channel> proxy(Channel channel, NetLocation netLocation) {
        Promise<Channel> promise=channel.eventLoop().newPromise();
        SocksClientNegotiator negotiator = new SocksClientNegotiator(channel,
                new Destination(netLocation));
        Async.cascadeFailure(negotiator.handshake(),promise,future->{
            if (netLocation.getTp() == TP.TCP) {
                promise.trySuccess(channel);
            }else{
                NetAddr netAddr = future.get();
                Future<Channel> channelFuture = transport.createChannel(channel.eventLoop(),
                        new Destination(netAddr, TP.UDP), netLocation.getSrc().isIpv4()? AddrType.IPV4:AddrType.IPV6);
                Async.cascadeFailure(channelFuture, promise, f-> {
                    Channel udpChannel = f.get();
                    udpChannel.pipeline().addLast(new SocksUdpHandler(netAddr));
                    channel.attr(IdleTimeoutHandler.IGNORE_IDLE_FLAG);
                    channel.closeFuture().addListener(it->safeClose(udpChannel));
                    udpChannel.closeFuture().addListener(it->safeClose(channel));
                    promise.trySuccess(udpChannel);
                });
            }
        });
        return promise;
    }

    private static class SocksUdpHandler extends ChannelDuplexHandler{
        private final NetAddr remote;

        public SocksUdpHandler(NetAddr remote) {
            this.remote = remote;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof DatagramPacket packet){
                ByteBuf content = packet.content();
                content.skipBytes(2);
                if (content.readByte()!=0){
                    ReferenceCountUtil.safeRelease(msg);
                    return;
                }
                InetSocketAddress sender = Socks.decodeAddr(content);
                content.discardReadBytes();
                ctx.fireChannelRead(new DatagramPacket(content,packet.recipient(),sender));
            }else{
                super.channelRead(ctx,msg);
            }

        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof DatagramPacket packet){
                ByteBuf addressBuf = ctx.alloc().buffer(4);
                addressBuf.writeByte(0).writeByte(0).writeByte(0);
                Socks.encodeAddr(packet.recipient(), addressBuf);
                ByteBuf content = Unpooled.compositeBuffer()
                                          .addComponent(true,addressBuf)
                                          .addComponent(true, packet.content());
                ctx.write(new DatagramPacket(content,remote.getAsInetAddr(),packet.sender()),promise);
            }else{
                super.write(ctx, msg, promise);
            }

        }
    }

}
