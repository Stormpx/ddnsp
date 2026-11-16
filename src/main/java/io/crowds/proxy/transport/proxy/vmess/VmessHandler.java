package io.crowds.proxy.transport.proxy.vmess;

import io.crowds.proxy.TP;
import io.crowds.proxy.transport.Destination;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;

import java.util.Set;

public class VmessHandler extends ChannelDuplexHandler {
    private final User user;
    private final Security security;
    private final Destination dst;
    private boolean handshake = false;

    public VmessHandler(User user, Security security, Destination dst) {
        this.user = user;
        this.security = security;
        this.dst = dst;
    }


    private void handshake(ChannelHandlerContext ctx){
        VmessRequest request = new VmessRequest(Set.of(Option.CHUNK_STREAM,Option.CHUNK_MASKING), dst.tp(), dst.addr());
        request.setUser(user.randomUser());
        request.setSecurity(security);

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
            if (dst.tp()== TP.UDP){
                msg=new DatagramPacket(buf,null,dst.addr().getAsInetAddr());
            }
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }
}
