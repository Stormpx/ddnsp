package io.crowds.proxy.transport.proxy.trojan;

import io.crowds.proxy.NetLocation;
import io.crowds.proxy.transport.Destination;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;

public class TrojanHandler extends ChannelOutboundHandlerAdapter {

    private TrojanOption trojanOption;
    private NetLocation netLocation;

    private boolean request;


    public TrojanHandler(TrojanOption trojanOption, NetLocation netLocation) {
        this.trojanOption = trojanOption;
        this.netLocation = netLocation;
    }


    private TrojanRequest newRequest(Object msg){
        this.request=true;
        var request=new TrojanRequest(trojanOption.getPassword(),new Destination(netLocation.getDest(),netLocation.getTp()));
        if (msg!=null) {
            if (msg instanceof ByteBuf buf) {
                request.setPayload(buf);
            } else if (msg instanceof DatagramPacket packet) {
                request.setPayload(packet);
            }
        }
        return request;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().eventLoop().execute(()->{
            if (!request){
                ctx.writeAndFlush(newRequest(null));
            }
        });
        super.handlerAdded(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!request){
            TrojanRequest request = newRequest(msg);
            super.write(ctx,request,promise);
        }else {
            super.write(ctx, msg, promise);
        }
    }
}
