package dns;

import io.crowds.dns.DnsUpstream;
import io.netty.handler.codec.dns.*;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class DnsUpstreamTest {


    protected abstract DnsUpstream dnsUpstream();

    @Test
    public void queryTest(){
        DnsUpstream dnsUpstream = dnsUpstream();
        var query = new DefaultDnsQuery(1, DnsOpCode.QUERY);
        query.setRecursionDesired(true);
        query.addRecord(DnsSection.QUESTION,new DefaultDnsQuestion("www.google.com.", DnsRecordType.A));

        List<Future<DnsResponse>> responses=new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            Promise<DnsResponse> promise = Promise.promise();
            Thread.ofVirtual().start(()->{
                dnsUpstream.lookup(query).onComplete(promise);
            });
            responses.add(promise.future());
        }

        CompositeFuture compositeFuture = Future.join(responses).await();

        assertTrue(compositeFuture.succeeded());
        for (int i = 0; i < compositeFuture.size(); i++) {
            DnsResponse resp = compositeFuture.resultAt(i);
            System.out.println(resp);
            assertEquals(DnsResponseCode.NOERROR,resp.code());
            assertEquals("www.google.com.",resp.recordAt(DnsSection.QUESTION,0).name());
            assertEquals(1,resp.count(DnsSection.ANSWER));
            assertEquals("www.google.com.",resp.recordAt(DnsSection.ANSWER,0).name());
            assertEquals(DnsRecordType.A,resp.recordAt(DnsSection.ANSWER,0).type());
            assertEquals(4,((DnsRawRecord)resp.recordAt(DnsSection.ANSWER,0)).content().readableBytes());
        }

    }

}
