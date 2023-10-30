package io.crowds.proxy.transport.proxy.vmess;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;

import java.util.Set;

public class VmessHandler extends ChannelDuplexHandler {

    private Channel channel;
    private VmessOption vmessOption;
    private NetLocation netLocation;


    public VmessHandler(Channel channel, VmessOption vmessOption, NetLocation netLocation) {
        this.channel = channel;
        this.vmessOption = vmessOption;
        this.netLocation = netLocation;
        this.channel.pipeline()
                .addLast(new VmessMessageCodec())
                .addLast(this);
    }



    public void handshake(){
        NetAddr dest = netLocation.getDst();
        User user = vmessOption.getUser();
        VmessRequest request = new VmessRequest(Set.of(Option.CHUNK_STREAM,Option.CHUNK_MASKING), netLocation.getTp(), dest);
        request.setUser(user.randomUser());
        request.setSecurity(vmessOption.getSecurity());

        channel.writeAndFlush(request);


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
        if (msg instanceof DatagramPacket packet){
            msg=packet.content();
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf buf) {
            if (netLocation.getTp()== TP.UDP){
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
