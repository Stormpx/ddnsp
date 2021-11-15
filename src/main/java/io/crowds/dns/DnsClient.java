package io.crowds.dns;


import io.crowds.Platform;
import io.crowds.util.DnsKit;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.codec.dns.*;
import io.netty.util.concurrent.ScheduledFuture;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.net.impl.PartialPooledByteBufAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DnsClient {
    private final Logger logger= LoggerFactory.getLogger(DnsClient.class);
    private AtomicLong reqId=new AtomicLong(0);

    private DnsOption dnsOption;
    private int curServersIndex=0;

    private EventLoopGroup eventLoopGroup;
    private DatagramChannel channel;

    private Map<Integer,QueryRequest> queryRequestMap;

    public DnsClient(EventLoopGroup eventLoopGroup, DnsOption dnsOption) {

        this.dnsOption = dnsOption;
        this.eventLoopGroup=eventLoopGroup;
        this.queryRequestMap=new HashMap<>();
        this.channel= Platform.getDatagramChannel();
        channel.config().setAllocator(PartialPooledByteBufAllocator.DEFAULT);
        this.channel.pipeline()
                .addLast(new DatagramDnsQueryEncoder())
                .addLast(new DatagramDnsResponseDecoder())
                .addLast(new SimpleChannelInboundHandler<DnsResponse>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DnsResponse msg) throws Exception {
                        QueryRequest request = queryRequestMap.get(msg.id());
                        if (request!=null){
                            request.resp(msg);
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


    public DnsClient setDnsOption(DnsOption dnsOption) {
        this.dnsOption = dnsOption;
        return this;
    }

    private int nextId(){
        return (int) (this.reqId.addAndGet(1)%65536L);
    }

    private InetSocketAddress getServer(){
        if (curServersIndex>= dnsOption.getDnsServers().size()){
            curServersIndex=0;
        }
        return dnsOption.getDnsServers().get(curServersIndex++);
    }


    public Future<DnsResponse> request(DnsQuery dnsQuery){

        int id = nextId();
        InetSocketAddress server = getServer();
        DatagramDnsQuery query = new DatagramDnsQuery(null, server, id, dnsQuery.opCode());

        DnsKit.msgCopy(dnsQuery,query,true);
//        logger.warn("server:{} query:{}",server,dnsQuery);

        QueryRequest request = new QueryRequest(id,server);
        queryRequestMap.put(id,request);

        channel.writeAndFlush(query)
                .addListener(future ->{
                    if (!future.isSuccess()){

                        logger.warn("send query: {} to server: {} failed cause:{}",dnsQuery,server,future.cause().getMessage());
                    }

                });
        return request.future();
    }


    public Future<DnsResponse> request(String target,DnsRecordType type){
        if (!target.endsWith("."))
            target+=".";
        int id = nextId();
        InetSocketAddress server = getServer();
        DatagramDnsQuery query = new DatagramDnsQuery(null, server, id, DnsOpCode.QUERY).setRecursionDesired(true);
        query.addRecord(DnsSection.QUESTION,new DefaultDnsQuestion(target,type,DnsRecord.CLASS_IN));

        QueryRequest request = new QueryRequest(id,server).failOnError();
        queryRequestMap.put(id,request);

        channel.writeAndFlush(query)
                .addListener(future ->{
                    if (logger.isDebugEnabled())
                        logger.debug("send query to server: {} result: {}",server,future.isSuccess());
                });
        return request.future();

    }


    public Future<InetAddress> request(String target){
        List<Future> fs=Stream.of(DnsRecordType.A,DnsRecordType.AAAA)
                .map(type -> request(target, type)
                        .map(resp->{
                            try {
                                int count = resp.count(DnsSection.ANSWER);
                                for (int i = 0; i < count; i++) {
                                    DnsRecord respRecord = resp.recordAt(DnsSection.ANSWER, i);
                                    if (respRecord.type()==type){
                                        DnsRawRecord record= (DnsRawRecord) respRecord;
                                        return InetAddress.getByAddress(ByteBufUtil.getBytes(record.content()));
                                    }
                                }
                                return null;
                            } catch (UnknownHostException e) {
                                //
                                return null;
                            }
                        })
                )
                .collect(Collectors.toList());
        return CompositeFuture.any(fs).map(cf->{
            for (int i = 0; i < cf.size(); i++) {
                if (cf.succeeded(i)){
                    return cf.resultAt(i);
                }
            }
            return null;
        });

    }


    class QueryRequest{
        private Integer id;
        private InetSocketAddress server;
        private boolean failOnError;

        private ScheduledFuture<?> schedule;

        private Promise<DnsResponse> promise;


        public QueryRequest(Integer id,InetSocketAddress server) {
            this.id=id;
            this.server=server;
            this.schedule=eventLoopGroup.schedule(()->{
                promise.tryFail(server+" dns query timeout");
                release();
            },5,TimeUnit.SECONDS);
            this.promise=Promise.promise();

        }

        public QueryRequest failOnError() {
            this.failOnError = true;
            return this;
        }

        public void resp(DnsResponse response){
            if (!failOnError || response.code()==DnsResponseCode.NOERROR){
                promise.tryComplete(response);
            }else{
                promise.tryFail(server+" dns query error "+response.code().toString());
            }
            release();
        }

        public void release(){
            if (!this.schedule.isCancelled())
                this.schedule.cancel(false);

            queryRequestMap.remove(id);
        }

        public Future<DnsResponse> future() {
            return promise.future();
        }
    }

}
