package io.crowds.dns;

import io.crowds.util.Async;
import io.crowds.util.DatagramChannelFactory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.codec.dns.*;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class UdpUpstream extends AbstractDnsUpstream {
    private final static Logger logger= LoggerFactory.getLogger(UdpUpstream.class);

    private final EventLoop eventLoop;
    private final DatagramChannelFactory<? extends DatagramChannel> channelFactory;
    private final InetSocketAddress defaultAddr;


    private final Slot[] slots = new Slot[4];
    private final AtomicInteger index =new AtomicInteger(0);


    public UdpUpstream(EventLoop eventLoop, DatagramChannelFactory<? extends DatagramChannel> channelFactory, InetSocketAddress defaultAddr) {
        super(null);
        if (defaultAddr.isUnresolved()){
            throw new RuntimeException("unresolved address");
        }
        this.eventLoop = eventLoop;
        this.channelFactory = channelFactory;
        this.defaultAddr=defaultAddr;
        for (int i = 0; i < 4; i++) {
            this.slots[i] = new Slot();
        }
    }


    public Future<DnsResponse> lookup(DnsQuery query,InetSocketAddress remote) {
        int idx = index.getAndIncrement() & 3;
        Slot slot = slots[idx];
        Conn conn = slot.getConn();
        return Async.toFuture(conn.lookup(query,remote));
    }

    @Override
    public Future<DnsResponse> lookup(DnsQuery query) {
        Objects.requireNonNull(defaultAddr,"default address is null");
        return lookup(query, defaultAddr);
    }

    private class Slot {
        final ReentrantLock lock=new ReentrantLock();
        volatile Conn conn;

        Conn getConn(){
            Conn conn = this.conn;
            if (conn==null|| conn.close){
                lock.lock();
                try {
                    conn = this.conn;
                    if (conn==null||conn.close){
                        conn = new Conn(channelFactory.newChannel());
                        this.conn = conn;
                    }
                }finally {
                    lock.unlock();
                }
            }
            return conn;
        }

    }

    private class Conn{
        final Map<Integer,QueryContext> queryContextMap = new ConcurrentHashMap<>();
        final AtomicInteger reqId=new AtomicInteger(0);
        final DatagramChannel channel;
        io.netty.util.concurrent.Promise<Void> bindPromise;
        volatile boolean close;
        long usedCounter = 0;

        private Conn(DatagramChannel channel) {
            this.channel = channel;
            this.close = false;
            setupPipeline(channel);
        }

        private int getNextId(){
            int id = reqId.addAndGet(1);
            return id & 65535;
        }

        private void setupPipeline(DatagramChannel channel){
            channel.pipeline()
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
        }

        io.netty.util.concurrent.Promise<Void> doBind(DatagramChannel channel){
            io.netty.util.concurrent.Promise<Void> promise = this.bindPromise;
            if (promise==null){
                promise = eventLoop.newPromise();
                Promise<Void> finalPromise = promise;
                Async.cascadeFailure(eventLoop.register(channel),promise, f->{
                    channel.bind(new InetSocketAddress(0)).addListener(Async.cascade(finalPromise));
                });
                this.bindPromise = promise;
            }
            return promise;
        }

        void cleanup(int id, QueryContext context){
            usedCounter++;
            queryContextMap.remove(id,context);
            if (usedCounter>512){
                if (!close){
                    close = true;
                    eventLoop.schedule(()->{
                        channel.close();
                    },11,TimeUnit.SECONDS);
                }
            }
        }


        void lookup(DnsQuery query, InetSocketAddress remote, io.netty.util.concurrent.Promise<DnsResponse> promise) {
            int id = getNextId();
            DatagramDnsQuery datagramDnsQuery = new DatagramDnsQuery(null, remote, id, query.opCode());
            DnsKit.msgCopy(query,datagramDnsQuery,true);
            //            logger.info("send udp query to {}",remote);

            QueryContext context = new QueryContext(promise);
            queryContextMap.put(id, context);

            channel.writeAndFlush(datagramDnsQuery);

            promise.addListener(f-> cleanup(id,context));

        }

        io.netty.util.concurrent.Promise<DnsResponse> lookup(DnsQuery query,InetSocketAddress remote){
            io.netty.util.concurrent.Promise<DnsResponse> promise = eventLoop.newPromise();
            eventLoop.execute(()->{
                if (this.bindPromise==null||!this.bindPromise.isDone()){
                    Async.cascadeFailure(doBind(channel),promise,f->{
                        lookup(query,remote,promise);
                    });
                }else {
                    lookup(query,remote,promise);
                }
            });

            return promise;
        }

    }

    private class QueryContext{
        private final io.netty.util.concurrent.Promise<DnsResponse> promise;
        private final ScheduledFuture<?> schedule;


        public QueryContext(Promise<DnsResponse> promise) {
            this.promise = promise;
            this.schedule = eventLoop.schedule(() -> {
                promise.setFailure(new TimeoutException("timeout.."));
            }, 5, TimeUnit.SECONDS);
        }

        void cancelSchedule(){
            if (!this.schedule.isDone()){
                this.schedule.cancel(false);
            }
        }

        public void callback(DnsResponse response){
            cancelSchedule();
            if (response.isTruncated()){
                this.promise.setFailure(new RuntimeException("response truncated"));
            }else {
                this.promise.setSuccess(response);
            }
        }

    }
}
