package io.crowds.proxy.transport.direct;

import io.crowds.proxy.transport.EndPoint;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.Future;

public class TcpEndPoint extends EndPoint {

    private Channel channel;

    public TcpEndPoint(Channel channel) {
        this.channel = channel;
        init();
    }

    private void init(){
        this.channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>(false) {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                fireBuf(msg);
            }
        });
    }

    @Override
    public void write(ByteBuf buf) {
        channel.writeAndFlush(buf);
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public void close() {
        if (channel.isActive())
            channel.close();
    }

    @Override
    public Future<Void> closeFuture() {
        return this.channel.closeFuture();
    }

}
