package io.crowds.proxy;

import io.crowds.proxy.transport.EndPoint;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private final static Logger logger= LoggerFactory.getLogger(ProxyChannelHandler.class);

    private ProxyContext proxyContext;
    private int direction;

    public ProxyChannelHandler(ProxyContext proxyContext, int direction) {
        super(false);
        this.proxyContext = proxyContext;
        this.direction = direction;
    }

    private EndPoint getEndPoint(){
        if (direction==1){
            return proxyContext.getSrc();
        }else{
            return proxyContext.getDest();
        }
    }



    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        logger.info("direction {} channel closed",direction==0?"src":"dest");

    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {

        EndPoint e = getEndPoint();
        e.write(msg);

    }


    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.info("channel exception {} {}",ctx.channel().isActive(),ctx.channel().isOpen());
        logger.error("exception occurred type:{} message: {}",cause.getClass().getName(),cause.getMessage());
    }
}
