package io.crowds.proxy.transport.proxy.vmess;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.proxy.xudp.XUDPHandler;
import io.crowds.proxy.transport.proxy.xudp.XUDPOption;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;

import java.util.Set;

public class VmessHandler extends ChannelDuplexHandler {
    private final Channel channel;
    private final User user;
    private final Security security;
    private final boolean xudp;
    private final NetLocation netLocation;
    private boolean handshake = false;

    public VmessHandler(Channel channel, User user, Security security, boolean xudp, NetLocation netLocation) {
        this.channel = channel;
        this.user = user;
        this.security = security;
        this.xudp = xudp;
        this.netLocation = netLocation;
    }


    private void handshake(ChannelHandlerContext ctx){
        NetAddr dest = netLocation.getDst();
        VmessRequest request = new VmessRequest(Set.of(Option.CHUNK_STREAM,Option.CHUNK_MASKING), netLocation.getTp(), dest);
        request.setUser(user.randomUser());
        request.setSecurity(security);
        if (netLocation.getTp()== TP.UDP && xudp){
            request.setAddr(XUDPHandler.ADDRESS);
        }

        ctx.write(request);
        handshake = true;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        super.close(ctx, promise);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!handshake){
            handshake(ctx);
        }
        if (msg instanceof DatagramPacket packet){
            msg=packet.content();
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof VmessMessage){
            //do nothing
            return;
        }
        if (msg instanceof ByteBuf buf) {
            if (netLocation.getTp()== TP.UDP && !xudp){
                msg=new DatagramPacket(buf,null,netLocation.getDst().getAsInetAddr());
            }
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }
}
