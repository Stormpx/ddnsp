package io.crowds.proxy.services.tun;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.tun.*;
import org.stormpx.net.buffer.ByteArray;
import org.stormpx.net.network.Iface;
import org.stormpx.net.network.IfaceEntry;
import org.stormpx.net.network.NetworkParams;

import java.nio.ByteBuffer;

public class TunIface implements Iface {

    private final DefaultEventLoop eventLoop;
    private final String name;
    private TunChannel tunChannel;

    public TunIface(DefaultEventLoop eventLoop, String name) {
        this.eventLoop = eventLoop;
        this.name = name;
    }


    @Override
    public void init(NetworkParams networkParams, IfaceEntry ifaceEntry) {
        try {
            final Bootstrap b = new Bootstrap()
                    .group(eventLoop)
                    .channel(TunChannel.class)
                    .option(TunChannelOption.TUN_MTU, networkParams.mtu())
                    .handler(new SimpleChannelInboundHandler<TunPacket>(true) {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, TunPacket msg) throws Exception {
                            ByteBuf buf = msg.content();
                            ByteArray buffer = ByteArray.alloc(buf.readableBytes());
                            ByteBuffer byteBuffer = buf.nioBuffer();
                            buffer.setBuffer(0,ByteArray.wrap(byteBuffer),0,byteBuffer.limit());
                            ifaceEntry.enqueue(buffer);
                        }

                        @Override
                        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                            ifaceEntry.callback();
                            super.channelReadComplete(ctx);
                        }
                    });
             tunChannel = (TunChannel) b.bind(new TunAddress(name)).sync().channel();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean transmit(ByteArray data) {
        ByteBuffer[] byteBuffers = data.nioBuffers(0, data.length());
        ByteBuffer byteBuffer;
        if (byteBuffers.length==1){
            byteBuffer = byteBuffers[0];
        }else {
            byteBuffer = ByteBuffer.allocateDirect(data.length());
            for (ByteBuffer buffer : byteBuffers) {
                byteBuffer.put(buffer);
            }
            byteBuffer.flip();
        }
        tunChannel.writeAndFlush(new Tun4Packet(Unpooled.wrappedBuffer(byteBuffer)));
        return true;
    }

    @Override
    public void destroy() {
        try {
            tunChannel.close().sync();
            eventLoop.close();
        } catch (InterruptedException e) {
            //ignore
        }
    }

}
