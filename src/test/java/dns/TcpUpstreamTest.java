package dns;

import io.crowds.dns.DnsCli;
import io.crowds.dns.TcpUpstream;
import io.crowds.dns.UdpUpstream;
import io.crowds.dns.cache.DnsCache;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.SingleThreadIoEventLoop;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.dns.*;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TcpUpstreamTest {

    @Test
    public void test(){
        MultiThreadIoEventLoopGroup eventLoopGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());

        TcpUpstream tcpUpstream = new TcpUpstream(eventLoopGroup, NioSocketChannel::new, new InetSocketAddress("114.114.114.114", 53));

        var query = new DefaultDnsQuery(1, DnsOpCode.QUERY);
        query.setRecursionDesired(true);
        query.addRecord(DnsSection.QUESTION,new DefaultDnsQuestion("www.google.com.", DnsRecordType.A));

        List<Future<DnsResponse>> responses=new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            Promise<DnsResponse> promise = Promise.promise();
            Thread.ofVirtual().start(()->{
                tcpUpstream.lookup(query).onComplete(promise);
            });
            responses.add(promise.future());
        }

        CompositeFuture compositeFuture = Future.join(responses).await();

        assertTrue(compositeFuture.succeeded());
        for (int i = 0; i < compositeFuture.size(); i++) {
            DnsResponse resp = compositeFuture.resultAt(i);
            System.out.println(resp);
            assertEquals("www.google.com.",resp.recordAt(DnsSection.QUESTION,0).name());
            assertEquals(1,resp.count(DnsSection.ANSWER));
            assertEquals("www.google.com.",resp.recordAt(DnsSection.ANSWER,0).name());
            assertEquals(DnsRecordType.A,resp.recordAt(DnsSection.ANSWER,0).type());
            assertEquals(4,((DnsRawRecord)resp.recordAt(DnsSection.ANSWER,0)).content().readableBytes());
        }
    }

}
