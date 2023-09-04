package io.crowds.proxy.transport.proxy.vless;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.Destination;
import io.crowds.util.Async;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class VlessHandler extends ChannelDuplexHandler {
    private UUID id;
    private Destination dest;
    private boolean request;
    public VlessHandler(UUID id, Destination dest) {
        this.id = id;
        this.dest = dest;
    }


    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().eventLoop().schedule(()->{
            if (!request){
                ctx.writeAndFlush(new VlessRequest(id,dest));
                request=true;
            }
        },50, TimeUnit.MILLISECONDS);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!request){
            ctx.writeAndFlush(new VlessRequest(id,dest).setPayload(msg));
            request=true;
            return;
        }
        super.write(ctx, msg, promise);
    }


}
