package io.crowds.proxy.transport.block;

import io.crowds.proxy.AbstractProxyTransport;
import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.transport.EndPoint;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.SucceededFuture;

public class BlockProxyTransport extends AbstractProxyTransport {
    private final static Channel BLOCK_CHANNEL =new EmbeddedChannel();

    public BlockProxyTransport(EventLoopGroup eventLoopGroup, ChannelCreator channelCreator) {
        super(eventLoopGroup, channelCreator);
    }

    @Override
    public Future<EndPoint> createEndPoint(NetLocation netLocation) throws Exception {
        EventLoop eventLoop = eventLoopGroup.next();
        return new SucceededFuture<>(eventLoop,new Block(new DefaultPromise<>(eventLoop)));
    }

    @Override
    public String getTag() {
        return "block";
    }

    public class Block extends EndPoint{
        private Promise<Void> closePromise;

        public Block(Promise<Void> closePromise) {
            this.closePromise = closePromise;
        }

        @Override
        public void write(ByteBuf buf) {
            ReferenceCountUtil.safeRelease(buf);
            closePromise.trySuccess(null);
        }

        @Override
        public Channel channel() {
            return BLOCK_CHANNEL;
        }

        @Override
        public void close() {

        }

        @Override
        public Future<Void> closeFuture() {
            return closePromise;
        }
    }
}
