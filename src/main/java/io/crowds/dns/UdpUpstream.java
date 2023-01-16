package io.crowds.dns;

import io.crowds.Platform;
import io.crowds.util.DnsKit;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.codec.dns.*;
import io.netty.util.concurrent.ScheduledFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.impl.PartialPooledByteBufAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class UdpUpstream implements DnsUpstream {
    private final static Logger logger= LoggerFactory.getLogger(UdpUpstream.class);

    private EventLoopGroup eventLoopGroup;
    private DatagramChannel channel;

    private InetSocketAddress upAddr;
    private Map<Integer,QueryContext> queryContextMap;

    private int nextId=0;

    public UdpUpstream(EventLoopGroup eventLoopGroup, InetSocketAddress upAddr) {
        this.eventLoopGroup = eventLoopGroup;
        this.channel= Platform.getDatagramChannel();
        this.queryContextMap=new HashMap<>();
        this.upAddr=upAddr;
        channel.config().setAllocator(PartialPooledByteBufAllocator.DEFAULT);
        this.channel.pipeline()
                .addLast(new DatagramDnsQueryEncoder())
                .addLast(new DatagramDnsResponseDecoder())
                .addLast(new SimpleChannelInboundHandler<DnsResponse>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DnsResponse msg) throws Exception {
                        QueryContext context = queryContextMap.get(msg.id());
                        if (context!=null){
                            context.callback(msg);
                        }
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        if (logger.isDebugEnabled()){
                            logger.error("",cause);
                        }else{
                            logger.warn(cause.getMessage());
                        }
                    }
                });
        this.eventLoopGroup.register(channel);
        this.channel.bind(new InetSocketAddress("0.0.0.0",0));
    }

    private int getNextId(){
        var id =nextId++;
        nextId%=65536;
        return id;
    }

    @Override
    public Future<DnsResponse> lookup(DnsQuery query) {
        int id = getNextId();
        DatagramDnsQuery datagramDnsQuery = new DatagramDnsQuery(null, upAddr, id, query.opCode());
        DnsKit.msgCopy(query,datagramDnsQuery,true);

        channel.writeAndFlush(datagramDnsQuery);
        Promise<DnsResponse> promise = Promise.promise();
        QueryContext context = new QueryContext(promise);
        queryContextMap.put(id, context);

        return promise.future().onComplete(ar->queryContextMap.remove(id));
    }

    class QueryContext{
        private Promise<DnsResponse> promise;
        private ScheduledFuture<?> schedule;


        public QueryContext(Promise<DnsResponse> promise) {
            this.promise = promise;
            this.schedule = eventLoopGroup.schedule(() -> {
                promise.tryFail("timeout..");
            }, 5, TimeUnit.SECONDS);
        }

        void cancelSchedule(){
            if (!this.schedule.isDone()){
                this.schedule.cancel(false);
            }
        }

        public void callback(DnsResponse response){
            this.promise.tryComplete(response);
            cancelSchedule();
        }

    }
}
