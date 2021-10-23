package io.crowds.dns;


import io.crowds.Platform;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.codec.dns.*;
import io.netty.util.concurrent.ScheduledFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.net.impl.PartialPooledByteBufAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DnsClient {
    private final Logger logger= LoggerFactory.getLogger(DnsClient.class);
    private int curId=1;

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
                });
        this.eventLoopGroup.register(channel);
        this.channel.bind(new InetSocketAddress("0.0.0.0",0));

    }


    public DnsClient setDnsOption(DnsOption dnsOption) {
        this.dnsOption = dnsOption;
        return this;
    }

    private int nextId(){
        if (curId>65535){
            curId=1;
        }
        return curId++;
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

        DnsKit.msgCopy(dnsQuery,query,false);

        QueryRequest request = new QueryRequest(id);
        queryRequestMap.put(id,request);

        channel.writeAndFlush(query)
                .addListener(future ->{
                    if (!future.isSuccess()){

                        logger.info("send query to server: {} failed cause:{}",server,future.cause().getMessage());
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

        QueryRequest request = new QueryRequest(id);
        queryRequestMap.put(id,request);

        channel.writeAndFlush(query)
                .addListener(future ->{
                    logger.info("send query to server: {} result: {}",server,future.isSuccess());
                });
        return request.future();

    }



    class QueryRequest{
        private Integer id;

        private ScheduledFuture<?> schedule;

        private Promise<DnsResponse> promise;


        public QueryRequest(Integer id) {
            this.id=id;
            this.schedule=eventLoopGroup.schedule(()->{
                promise.tryFail("dns query timeout");
                release();
            },5,TimeUnit.SECONDS);
            this.promise=Promise.promise();

        }

        public void resp(DnsResponse response){
            promise.tryComplete(response);
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
