package io.crowds.dns;

import io.crowds.Ddnsp;
import io.crowds.Platform;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.handler.codec.dns.*;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.impl.PartialPooledByteBufAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class UdpUpstream extends AbstractDnsUpstream {
    private final static Logger logger= LoggerFactory.getLogger(UdpUpstream.class);

    private EventLoopGroup eventLoopGroup;
    private DatagramChannel channel;

    private InetSocketAddress defaultAddr;
    private Map<Integer,QueryContext> queryContextMap;

    private AtomicInteger reqId=new AtomicInteger(0);
    public UdpUpstream(EventLoopGroup eventLoopGroup, InetSocketAddress defaultAddr) {
        super(null);
        if (defaultAddr.isUnresolved()){
            throw new RuntimeException("unresolved address");
        }
        this.eventLoopGroup = eventLoopGroup;
        this.queryContextMap=new HashMap<>();
        init(defaultAddr);
    }

    public UdpUpstream(EventLoopGroup eventLoopGroup, InetSocketAddress defaultAddr,InternalDnsResolver internalDnsResolver) {
        super(internalDnsResolver);
        this.eventLoopGroup = eventLoopGroup;
        this.queryContextMap=new HashMap<>();
        bootLookup(defaultAddr).onSuccess(this::init);
    }

    private void init(InetSocketAddress defaultAddr){
        boolean ipv6=defaultAddr.getAddress() instanceof Inet6Address;
        this.channel= Platform.getDatagramChannel(ipv6? InternetProtocolFamily.IPv6:InternetProtocolFamily.IPv4);
        this.defaultAddr=defaultAddr;
        this.channel.pipeline()
                .addLast(new DatagramDnsQueryEncoder())
                .addLast(new DatagramDnsResponseDecoder())
                .addLast(new SimpleChannelInboundHandler<DnsResponse>(false) {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DnsResponse msg) throws Exception {
                        QueryContext context = queryContextMap.get(msg.id());
                        if (context!=null){
                            context.callback(msg);
                        }else {
                            ReferenceCountUtil.safeRelease(msg);
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
        this.channel.bind(new InetSocketAddress(0));
    }

    private int getNextId(){
        int id = reqId.addAndGet(1);
        if(id>65535){
            reqId.compareAndSet(id,id%65535);
            id%=65535;
        }
        return id;
    }


    public Future<DnsResponse> lookup(DnsQuery query,InetSocketAddress remote) {
        int id = getNextId();
        DatagramDnsQuery datagramDnsQuery = new DatagramDnsQuery(null, remote, id, query.opCode());
        DnsKit.msgCopy(query,datagramDnsQuery,true);
//            logger.info("send udp query to {}",remote);

        Promise<DnsResponse> promise = Promise.promise();
        QueryContext context = new QueryContext(promise);
        queryContextMap.put(id, context);

        channel.writeAndFlush(datagramDnsQuery);

        return promise.future().onComplete(ar->queryContextMap.remove(id));
    }

    @Override
    public Future<DnsResponse> lookup(DnsQuery query) {
        Objects.requireNonNull(defaultAddr,"default address is null");
        return lookup(query, defaultAddr);
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
            cancelSchedule();
            this.promise.tryComplete(response);
        }

    }
}
