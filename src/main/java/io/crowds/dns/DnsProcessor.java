package io.crowds.dns;

import io.crowds.dns.server.DnsContext0;
import io.crowds.dns.server.DnsRequest;
import io.netty.handler.codec.dns.*;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class DnsProcessor {
    private final static Logger logger= LoggerFactory.getLogger(DnsProcessor.class);
    private DnsClient dnsClient;
    private DnsOption option;


    private Handler<DnsContext0> context0Handler=ctx->ctx.doQuery(ctx::resp);

    public DnsProcessor(DnsClient dnsClient) {
        this.dnsClient = dnsClient;
    }

    public DnsProcessor contextHandler(Handler<DnsContext0> contextHandler) {
        Objects.requireNonNull(contextHandler);
        this.context0Handler = contextHandler;
        return this;
    }

    public DnsProcessor setOption(DnsOption option) {
        this.option = option;
        return this;
    }

    private boolean isContainsAAAAQuestion(List<DnsQuestion> questions){
        for (DnsQuestion question : questions) {
            if (Objects.equals(question.type(), DnsRecordType.AAAA)) {
                return true;
            }
        }
        return false;
    }


    public void process(DnsRequest request){
        DnsQuery dnsQuery = request.query();
        if (dnsQuery.count(DnsSection.QUESTION)==0){
            return;
        }
        if (dnsQuery.count(DnsSection.ANSWER)!=0){
            return;
        }
        DnsContext0 dnsContext0 = new DnsContext0(request,this::doQuery);

        if (logger.isDebugEnabled())
            logger.debug("query :{}" ,dnsContext0.getQuery());
        try {

            List<DnsQuestion> questions = dnsContext0.getQuestions();
            if (!option.isIpv6()&& isContainsAAAAQuestion(questions)){
                dnsContext0.resp(DnsOpCode.QUERY, DnsResponseCode.NXDOMAIN,List.of());
                return;
            }
            if (dnsContext0.isQuery()&& questions.size()==1) {
                DnsQuestion question= questions.getFirst();
                DnsRecordType questionRType = question.type();
                if (option!=null){
                    Map<String, RecordData> rrMap = Optional.ofNullable(option.getRrMap()).orElse(Collections.emptyMap());
                    RecordData recordData = rrMap.get(question.name());
                    if (recordData!=null){
                        List<DnsRecord> records = recordData.get(questionRType);
                        if (records!=null&&!records.isEmpty()){
                            logger.info("Question: {} match static record: {}",question,records);
                            dnsContext0.resp(DnsOpCode.QUERY,DnsResponseCode.NOERROR, records.stream().map(it->DnsKit.clone(it,option.getTtl())).toList());
                            return;
                        }

                    }
                }
            }
            context0Handler.handle(dnsContext0);

        } catch (Exception e) {
            logger.error("",e);
        }

    }

    public Future<DnsResponse> doQuery(DnsContext0 context0){
        return dnsClient.request(context0.getQuery());
    }

}
