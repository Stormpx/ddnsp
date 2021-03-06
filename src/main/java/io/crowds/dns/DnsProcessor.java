package io.crowds.dns;

import io.crowds.util.DnsKit;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.codec.dns.*;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class DnsProcessor {
    private final static Logger logger= LoggerFactory.getLogger(DnsProcessor.class);
    private DatagramChannel channel;
    private DnsClient dnsClient;
    private DnsCache dnsCache;
    private DnsOption option;

    private Handler<DnsContext> contextHandler=ctx->ctx.recursionQuery(ctx::resp);

    public DnsProcessor(DatagramChannel channel, DnsClient dnsClient, DnsCache dnsCache) {
        this.channel = channel;
        this.dnsClient = dnsClient;
        this.dnsCache = dnsCache;
    }

    public DnsProcessor contextHandler(Handler<DnsContext> contextHandler) {
        Objects.requireNonNull(contextHandler);
        this.contextHandler = contextHandler;
        return this;
    }

    public DnsProcessor setOption(DnsOption option) {
        this.option = option;
        return this;
    }

    private boolean isLocal(DnsMessage message){
        return message.opCode()== DnsOpCode.QUERY&&message.count(DnsSection.QUESTION)==1;
    }


    public void process(DnsQuery dnsQuery){
        DatagramDnsQuery datagramDnsQuery= (DatagramDnsQuery) dnsQuery;
        if (logger.isDebugEnabled())
            logger.debug("query :{}" ,datagramDnsQuery);
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
                            logger.info("question {} match static record :{}",question,records);
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

                DnsContext context = new DnsContext(datagramDnsQuery.id(), datagramDnsQuery.recipient(), datagramDnsQuery.sender(),
                        question, channel, q->dnsClient.request(datagramDnsQuery));

                contextHandler.handle(context);
                //cache query
//                recursionQueryWithCache(question,new ArrayList<>())
//                        .onFailure(t->logger.error("",t))
//                        .onSuccess(resp->{
//                            var response = new DatagramDnsResponse(datagramDnsQuery.recipient(), datagramDnsQuery.sender(), datagramDnsQuery.id(),
//                                    datagramDnsQuery.opCode(),resp.code());
//                            response.addRecord(DnsSection.QUESTION, question);
//                            DnsKit.msgCopy(resp,response,true);
//                            channel.writeAndFlush(response)
//                                    .addListener(future -> {
//
//                                    });
//                        });
                return;
            }
            //just proxy
            recursionQuery(datagramDnsQuery);

        } catch (Exception e) {
            logger.info("",e);
        }

    }

    void recursionQuery(DatagramDnsQuery datagramDnsQuery){
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

    private String getCachedCname(String name,List<DnsRecord> result){
        List<DnsRecord> records = dnsCache.get(name, DnsRecordType.CNAME);
        if (records==null||records.isEmpty()){
            return null;
        }
        DnsRecord record = records.get(0);
        result.add(((DefaultDnsRawRecord)record).retain());
        String domainName = DnsKit.decodeDomainName(((DefaultDnsRawRecord) record).content());
        return domainName;
    }

     Future<DnsResponse> recursionQueryWithCache(DnsRecord record, List<DnsRecord> result){
        String name = record.name();
        DnsRecordType type = record.type();
        List<DnsRecord> cacheDnsRecords = dnsCache.get(name, type);
        if (cacheDnsRecords!=null&&!cacheDnsRecords.isEmpty()) {
            result.addAll(cacheDnsRecords);
            logger.info("{} {} hit cache",name,type.name());
            var resp=new DefaultDnsResponse(0,DnsOpCode.QUERY,DnsResponseCode.NOERROR);
            resp.setRecursionDesired(true);
            for (DnsRecord dnsRecord : result) {
                resp.addRecord(DnsSection.ANSWER,dnsRecord);
            }
            return Future.succeededFuture(resp);
        }else if (type!=DnsRecordType.CNAME){
            String cachedCname = getCachedCname(name,result);
            if (cachedCname!=null) {
//                result.add(new DefaultDnsRawRecord())
                var newQuestion = new DefaultDnsQuestion(cachedCname, type, record.dnsClass());
                return recursionQueryWithCache(newQuestion, result);
            }
        }

        DnsQuery query = new DefaultDnsQuery(-1, DnsOpCode.QUERY);
        query.setRecursionDesired(true);
        query.addRecord(DnsSection.QUESTION,new DefaultDnsQuestion(name,type,record.dnsClass()));

        var future = dnsClient.request(query)
                .onSuccess(resp->{
                    int count = resp.count(DnsSection.ANSWER);
                    for (int i = 0; i < count; i++) {
                        DnsRecord respRecord = resp.recordAt(DnsSection.ANSWER, i);
                        dnsCache.cache(respRecord.name(),respRecord);
                        result.add(DnsKit.clone(respRecord));
                    }
                });
        query.release();
        return future;
    }

}
