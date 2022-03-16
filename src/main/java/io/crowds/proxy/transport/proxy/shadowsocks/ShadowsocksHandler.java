package io.crowds.proxy.transport.proxy.shadowsocks;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;

public class ShadowsocksHandler extends ChannelOutboundHandlerAdapter {

    private Channel channel;
    private ShadowsocksOption option;
    private NetLocation netLocation;
    private NetAddr serverAddr;

    public ShadowsocksHandler(Channel channel, ShadowsocksOption option, NetLocation netLocation) {
        this.channel = channel;
        this.option = option;
        this.netLocation = netLocation;
        init();

        channel.writeAndFlush(encodeAddress(channel.alloc(),netLocation.getDest()));
    }

    public ShadowsocksHandler(Channel channel, ShadowsocksOption option, NetAddr serverAddr) {
        this.channel = channel;
        this.option = option;
        this.netLocation=new NetLocation(null,serverAddr,TP.UDP);
        this.serverAddr=serverAddr;
        init();
    }

    private void init(){
        this.channel.pipeline()
                .addLast("AEADCodec", netLocation.getTp() == TP.TCP ? AEADCodec.tcp(option) : AEADCodec.udp(option))
                .addLast(this);
    }

    private ByteBuf encodeAddress(ByteBufAllocator alloc, NetAddr addr){
        ByteBuf addressBuffer = alloc.buffer(7);
        if (addr.isIpv4()){
            addressBuffer.writeByte(0x01);
        }else if (addr.isIpv6()){
            addressBuffer.writeByte(0x04);
        }else{
            addressBuffer.writeByte(0x03);
            String host = addr.getHost();
            if (host.length()>256){
                throw new IllegalArgumentException("dest domain "+ host +" to long");
            }
            addressBuffer.writeByte(host.length());
        }
        addressBuffer.writeBytes(addr.getByte()).writeShort(addr.getPort());
        return addressBuffer;
    }


    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {

        if (netLocation.getTp()== TP.TCP){
            super.write(ctx, msg, promise);
        }else{
            if (!(msg instanceof DatagramPacket packet)){
                ReferenceCountUtil.safeRelease(msg);
                return;
            }
            super.write(
                    ctx,
                    new DatagramPacket(Unpooled.compositeBuffer()
                            .addComponent(true,encodeAddress(ctx.alloc(),NetAddr.of(packet.recipient())))
                            .addComponent(true, packet.content()),serverAddr.getAsInetAddr()),
                    promise
            );
        }

    }
}
