package io.crowds.dns;


import io.crowds.Global;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.*;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.net.impl.PartialPooledByteBufAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.*;

public class DnsServer {
    private final static Logger logger= LoggerFactory.getLogger(DnsServer.class);
    private EventLoopGroup eventLoopGroup;
    private DatagramChannel channel;

    private DnsClient dnsClient;

    private DnsOption option;

    private DnsCache dnsCache;

    public DnsServer(EventLoopGroup eventLoopGroup,DnsClient dnsClient) {
        this.eventLoopGroup = eventLoopGroup;
        this.dnsClient=dnsClient;
        this.dnsCache=new DnsCache();
        this.channel= Global.getDatagramChannel();
        channel.config().setAllocator(PartialPooledByteBufAllocator.DEFAULT);
        this.channel.pipeline()
                .addLast(new DatagramDnsQueryDecoder())
                .addLast(new DatagramDnsResponseEncoder())
                .addLast(new SimpleChannelInboundHandler<DnsQuery>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DnsQuery msg) throws Exception {
                        handleQuery(msg);
                    }
                });


    }

    public DnsServer setOption(DnsOption option) {
        this.option = option;
        return this;
    }

    public Future<Void> start(SocketAddress socketAddress){
        Promise<Void> promise = Promise.promise();
        this.eventLoopGroup.register(channel);
        this.channel.bind(socketAddress).addListener(future -> {
            if (future.isSuccess()){
                logger.info("start dns server {} success",socketAddress);
                promise.tryComplete();
            }else{
                logger.info("start dns server {} failed cause:{}",socketAddress,future.cause().getMessage());
                promise.tryFail(future.cause());
            }
        });
        return promise.future();
    }

    private boolean isLocal(DnsMessage message){
        return message.opCode()==DnsOpCode.QUERY&&message.count(DnsSection.QUESTION)==1;
    }


    private void handleQuery(DnsQuery dnsQuery){
        DatagramDnsQuery datagramDnsQuery= (DatagramDnsQuery) dnsQuery;
        logger.info("query :{}" ,datagramDnsQuery);
        try {
            if (isLocal(datagramDnsQuery)) {
                DnsQuestion question=datagramDnsQuery.recordAt(DnsSection.QUESTION);
                DnsRecordType questionRType = question.type();
                if (option!=null){
                    Map<String, RecordData> rrMap = Optional.ofNullable(option.getRrMap()).orElse(Collections.emptyMap());
                    RecordData recordData = rrMap.get(question.name());
                    if (recordData!=null){
                        List<DnsRecord> records = recordData.get(questionRType);
                        if (records!=null&&!records.isEmpty()){
                            logger.info("match static record :{}",records);
                            DatagramDnsResponse response = new DatagramDnsResponse(datagramDnsQuery.recipient(), datagramDnsQuery.sender(), datagramDnsQuery.id());
                            response.addRecord(DnsSection.QUESTION, new DefaultDnsQuestion(question.name(), questionRType));
                            for (DnsRecord record : records) {
                                response.addRecord(DnsSection.ANSWER, DnsKit.clone(record,option.getTtl()));
                            }
                            channel.writeAndFlush(response).addListener(future -> {

                            });
                            return;
                        }

                    }
                }

                //cache query
                var response = new DatagramDnsResponse(datagramDnsQuery.recipient(), datagramDnsQuery.sender(), datagramDnsQuery.id());
                response.addRecord(DnsSection.QUESTION, question);
                recursionQueryWithCache(question,new ArrayList<>(),false)
                        .onFailure(t->logger.error("",t))
                        .onSuccess(result->{
                            for (DnsRecord record : result) {
                                response.addRecord(DnsSection.ANSWER,record);
                            }
                            channel.writeAndFlush(response)
                                    .addListener(future -> {

                                    });
                        });
                return;
            }
            //just proxy
            recursionQuery(datagramDnsQuery);

        } catch (Exception e) {
            logger.info("",e);
        }


    }

    private void recursionQuery(DatagramDnsQuery datagramDnsQuery){
        int id = datagramDnsQuery.id();
        dnsClient.request(datagramDnsQuery)
                .onFailure(t->logger.info("",t))
                .onSuccess(resp->{
                    var datagramDnsResponse=new DatagramDnsResponse(datagramDnsQuery.recipient(),datagramDnsQuery.sender(),id);
                    DnsKit.msgCopy(resp,datagramDnsResponse,true);
                    channel.writeAndFlush(datagramDnsResponse)
                            .addListener(future -> {

                            });


                });

    }

    private String getCachedCname(String name){
        List<DnsRecord> records = dnsCache.get(name, DnsRecordType.CNAME);
        if (records==null||records.isEmpty()){
            return null;
        }
        String domainName = DnsKit.decodeDomainName(((DefaultDnsRawRecord) records.get(0)).content());
        return domainName;
    }


    private Future<List<DnsRecord>> recursionQueryWithCache(DnsRecord record, List<DnsRecord> result, boolean cname){
        String name = record.name();
        DnsRecordType type = record.type();
        List<DnsRecord> cacheDnsRecords = dnsCache.get(name, type);
        if (cacheDnsRecords!=null&&!cacheDnsRecords.isEmpty()) {
            result.addAll(cacheDnsRecords);
            logger.info("{} {} hit cache",name,type.name());
            return Future.succeededFuture(result);
        }else if (type!=DnsRecordType.CNAME){
            String cachedCname = getCachedCname(name);
            if (cachedCname!=null) {
                var newQuestion = new DefaultDnsQuestion(cachedCname, type, record.dnsClass());
                return recursionQueryWithCache(newQuestion, result, false);
            }
        }

        DnsQuery query = new DefaultDnsQuery(-1, DnsOpCode.QUERY);
        query.setRecursionDesired(true);
        query.addRecord(DnsSection.QUESTION,new DefaultDnsQuestion(name,type,record.dnsClass()));

        var future = dnsClient.request(query)
                .map(resp->{
                    int count = resp.count(DnsSection.ANSWER);
                    for (int i = 0; i < count; i++) {
                        DnsRecord respRecord = resp.recordAt(DnsSection.ANSWER, i);
                        dnsCache.cache(respRecord.name(),respRecord);
                        result.add(DnsKit.clone(respRecord));
                    }
                    return result;
                });
        query.release();
        return future;
    }


}
