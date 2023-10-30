package io.crowds.proxy.transport.proxy.shadowsocks;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.Destination;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class ShadowsocksHandler extends ChannelDuplexHandler {
    private final static Logger logger= LoggerFactory.getLogger(ShadowsocksHandler.class);
    private ShadowsocksOption option;

    private NetLocation netLocation;
    private NetAddr serverAddr;

    private boolean writeAddr;

    private boolean protocolError;


    public ShadowsocksHandler( ShadowsocksOption option, NetLocation netLocation) {
        this.option = option;
        this.netLocation = netLocation;
        this.writeAddr=false;
//        init();

//        channel.writeAndFlush(encodeAddress(channel.alloc(),netLocation.getDest()));
    }

    public ShadowsocksHandler( ShadowsocksOption option, NetAddr serverAddr) {
        this.option = option;
        this.netLocation=new NetLocation(null,serverAddr,TP.UDP);
        this.serverAddr=serverAddr;
        this.writeAddr=true;
//        init();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (!writeAddr){
            ctx.channel().eventLoop().schedule(()->{
                if (!writeAddr){
                    writeAddr=true;
                    ctx.writeAndFlush(new ShadowsocksRequest(new Destination(netLocation.getDst(),netLocation.getTp())));
                }
            },50, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (netLocation.getTp()== TP.TCP){
            if (!writeAddr){
                assert msg instanceof ByteBuf;
                writeAddr=true;
                super.write(ctx,new ShadowsocksRequest(new Destination(netLocation.getDst(),netLocation.getTp())).setPayload((ByteBuf) msg),promise);
            }else {
                super.write(ctx, msg, promise);
            }
        }else{
            if (!(msg instanceof DatagramPacket packet)){
                ReferenceCountUtil.safeRelease(msg);
                return;
            }
            var request= new ShadowsocksRequest(new Destination(serverAddr,TP.UDP))
                    .setPayload(packet);
            super.write(ctx, request, promise);
        }

    }


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (this.protocolError){
            ReferenceCountUtil.safeRelease(msg);
            return;
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (this.protocolError){
            ctx.close();
        }
        super.channelReadComplete(ctx);
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof ShadowsocksException){
            this.protocolError=true;
            logger.error("",cause);
        }else{
            super.exceptionCaught(ctx, cause);
        }
    }
}
