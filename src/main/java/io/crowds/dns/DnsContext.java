package io.crowds.dns;

import io.crowds.util.DnsKit;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.codec.dns.*;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.Function;

public class DnsContext {
    private final static Logger logger= LoggerFactory.getLogger(DnsContext.class);
    private boolean resp=false;
    private int queryId;
    private InetSocketAddress recipient;
    private InetSocketAddress sender;
    private DnsQuestion question;
    private Channel channel;
    private Function<DnsQuestion, Future<DnsResponse>> queryFunction;

    public DnsContext(int queryId, InetSocketAddress recipient, InetSocketAddress sender, DnsQuestion question,
                      Channel channel, Function<DnsQuestion, Future<DnsResponse>> queryFunction) {
        this.queryId = queryId;
        this.recipient = recipient;
        this.sender = sender;
        this.question = question;
        this.channel = channel;
        this.queryFunction = queryFunction;
    }

    public void resp(DnsOpCode opCode, DnsResponseCode code, List<DnsRecord> answers){
        if (resp){
            return;
        }
        var response = new DatagramDnsResponse(recipient, sender, queryId,opCode,code);
        response.addRecord(DnsSection.QUESTION,question);
        if (answers!=null) {
            for (DnsRecord record : answers) {
                response.addRecord(DnsSection.ANSWER, record);
            }
        }
        channel.writeAndFlush(response)
                .addListener(future -> {

                });
        resp=true;
    }

    public void resp(DnsResponse resp){
        if (this.resp){
            return;
        }
        var response = new DatagramDnsResponse(recipient, sender, queryId, resp.opCode(),resp.code());
        response.addRecord(DnsSection.QUESTION, question);
        DnsKit.msgCopy(resp,response,true);
        channel.writeAndFlush(response)
                .addListener(future -> {

                });
        this.resp=true;
    }

    public void recursionQuery(Handler<DnsResponse> respHandler){
        queryFunction.apply(question)
                .onFailure(t->logger.error("",t))
                .onSuccess(resp->{
                    if (respHandler==null){
                        return;
                    }
                    respHandler.handle(resp);

                });
//        processor.recursionQueryWithCache(question,new ArrayList<>())

    }

    public int getQueryId() {
        return queryId;
    }

    public InetSocketAddress getRecipient() {
        return recipient;
    }

    public InetSocketAddress getSender() {
        return sender;
    }

    public DnsQuestion getQuestion() {
        return question;
    }
}
