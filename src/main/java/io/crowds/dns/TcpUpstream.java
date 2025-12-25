package io.crowds.dns;

import io.crowds.compoments.dns.DdnspAddressResolverGroup;
import io.crowds.compoments.dns.InternalDnsResolver;
import io.crowds.proxy.common.BaseChannelInitializer;
import io.crowds.util.Async;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.channel.pool.ChannelHealthChecker;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.dns.*;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import io.vertx.core.Future;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class TcpUpstream extends AbstractDnsUpstream{

    private final ChannelPool channelPool;
    private final AtomicLong reqId=new AtomicLong(0);

    public TcpUpstream(EventLoopGroup eventLoopGroup, ChannelFactory<? extends Channel> channelFactory, InetSocketAddress serverAddress) {
        super(null);
        if (serverAddress.isUnresolved()){
            throw new RuntimeException("Unresolved address");
        }
        var boostrap = new Bootstrap().group(eventLoopGroup)
                      .channelFactory(channelFactory)
                      .remoteAddress(serverAddress);
        this.channelPool=new FixedChannelPool(boostrap.clone(), new AbstractChannelPoolHandler() {
            @Override
            public void channelCreated(Channel ch) throws Exception {
                ch.pipeline().addLast(
                        new BaseChannelInitializer()
                                .initializer(new ChannelInitializer<>() {
                                    @Override
                                    protected void initChannel(Channel ch) throws Exception {
                                        ch.pipeline()
                                          .addLast(new TcpDnsResponseDecoder())
                                          .addLast(new TcpDnsQueryEncoder())
                                          .addLast("context", new DnsQueryContext(ch));
                                    }
                                })
                                .connIdle(120,((channel, idleStateEvent) -> channel.close()))
                );
            }
        }, ChannelHealthChecker.ACTIVE, FixedChannelPool.AcquireTimeoutAction.FAIL, 500L, 32, 32,
                true, true);
    }

    private int nextId(){
        long reqId = this.reqId.addAndGet(1);
        return (int) (reqId & 65535);
    }

    @Override
    public Future<DnsResponse> lookup(DnsQuery query) {
        int id = nextId();
        DefaultDnsQuery dnsQuery = new DefaultDnsQuery(id, query.opCode());
        DnsKit.msgCopy(query,dnsQuery,true);

        return Async.toFuture(channelPool.acquire())
             .compose(channel -> {
                 DnsQueryContext context = (DnsQueryContext) channel.pipeline().get("context");
                 assert context!=null;
                 return Async.toFuture(context.doLookup(dnsQuery));
             });
    }

    private final class DnsQueryContext extends ChannelDuplexHandler{

        private final Channel channel;
        private Promise<DnsResponse> promise;
        private ScheduledFuture<?> schedule;

        private DnsQueryContext(Channel channel) {
            this.channel = channel;
        }

        Promise<DnsResponse> doLookup(DnsQuery dnsQuery) {
            Promise<DnsResponse> promise = channel.eventLoop().newPromise();
            promise.addListener(future-> {
                this.promise = null;
                this.schedule = null;
                channelPool.release(channel);
            });
            this.promise = promise;
            Async.cascadeFailure(channel.writeAndFlush(dnsQuery),promise,v->{
                this.schedule = channel.eventLoop().schedule(()->{
                    if (!promise.isDone()){
                        channel.close();
                        promise.setFailure(new RuntimeException("timeout"));
                    }
                },5, TimeUnit.SECONDS);
            });
            return promise;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (!(msg instanceof DnsQuery)) {
                ReferenceCountUtil.safeRelease(msg);
                promise.setFailure(new RuntimeException("Message is not a DnsQuery"));
                return;
            }
            super.write(ctx,msg,promise);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!(msg instanceof DnsResponse response)) {
                ReferenceCountUtil.safeRelease(msg);
                return;
            }
            if (this.promise == null) {
                ReferenceCountUtil.safeRelease(msg);
                return;
            }
            this.promise.setSuccess(response);
            if (this.schedule!=null){
                this.schedule.cancel(false);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            Promise<DnsResponse> promise = this.promise;
            if (promise !=null&&!promise.isDone()){
                promise.setFailure(cause);
            }
            ctx.close();
        }
    }
}
