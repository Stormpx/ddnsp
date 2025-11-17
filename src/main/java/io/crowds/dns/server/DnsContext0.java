package io.crowds.dns.server;

import io.crowds.dns.DnsKit;
import io.netty.handler.codec.dns.*;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class DnsContext0 {

    private final static Logger logger= LoggerFactory.getLogger(DnsContext0.class);
    private boolean resp=false;
    private final int reqId;
    private final DnsRequest request;
    private final List<DnsQuestion> questions;
    private final Function<DnsContext0, Future<DnsResponse>> queryFunction;

    public DnsContext0(DnsRequest request, Function<DnsContext0, Future<DnsResponse>> queryFunction){
        this.reqId = request.id();
        this.request = request;
        this.queryFunction = queryFunction;

        DnsQuery dnsQuery = request.query();
        List<DnsQuestion> questions = new ArrayList<>();
        int count = dnsQuery.count(DnsSection.QUESTION);
        for (int i = 0; i < count; i++) {
            questions.add(dnsQuery.recordAt(DnsSection.QUESTION,i));
        }
        this.questions = questions;
    }


    public void resp(DnsOpCode opCode, DnsResponseCode code, List<DnsRecord> answers){
        if (resp){
            return;
        }

        var response = request.newResponse();
        response.setId(reqId);
        response.setOpCode(opCode);
        response.setCode(code);
        for (DnsQuestion question : this.questions) {
            response.addRecord(DnsSection.QUESTION, question);
        }
        if (answers!=null) {
            for (DnsRecord record : answers) {
                response.addRecord(DnsSection.ANSWER, record);
            }
        }
        this.request.response(response);
        resp=true;
    }

    public void resp(DnsResponse resp){
        if (this.resp){
            return;
        }
        var response = request.newResponse();
        response.setId(reqId);
        response.setOpCode(resp.opCode());
        response.setCode(resp.code());
        DnsKit.msgCopy(resp,response,true);
        response.clear(DnsSection.QUESTION);
        for (DnsQuestion question : this.questions) {
            response.addRecord(DnsSection.QUESTION, question);
        }
        this.request.response(response);
        this.resp=true;
    }

    public void doQuery(Handler<DnsResponse> respHandler){
        queryFunction.apply(this)
                     .onFailure(t->{
                         logger.error("",t);
                         ReferenceCountUtil.safeRelease(request);
                     })
                     .onSuccess(respHandler);
    }

    public void release(){
        ReferenceCountUtil.safeRelease(request);
    }

    public boolean isQuery(){
        return request.query().opCode()==DnsOpCode.QUERY;
    }

    public InetSocketAddress getSender(){
        return request.sender();
    }

    public DnsQuery getQuery() {
        return request.query();
    }

    public List<DnsQuestion> getQuestions() {
        return questions;
    }

}
