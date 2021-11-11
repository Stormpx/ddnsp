import io.crowds.dns.DnsClient;
import io.crowds.dns.DnsContext;
import io.crowds.dns.DnsOption;
import io.crowds.dns.DnsServer;
import io.crowds.proxy.dns.FakeContext;
import io.crowds.proxy.dns.FakeDns;
import io.crowds.proxy.routing.Router;
import io.crowds.util.IPCIDR;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.dns.*;
import io.vertx.core.Future;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public class FakeDnsTest {

    public static void main(String[] args) {
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        InetSocketAddress dnsServer1 = new InetSocketAddress("114.114.114.114", 53);
        DnsServer dnsServer = new DnsServer(eventLoopGroup, new DnsClient(eventLoopGroup, new DnsOption().setEnable(true).setDnsServers(Arrays.asList(dnsServer1))));

        FakeDns fakeDns = new FakeDns(eventLoopGroup.next(), new Router(Arrays.asList("kw;google;test","kw;youtube;test")), new IPCIDR("224.0.0.0/8"), new IPCIDR("fd12:3456:789a:bcde::/64"), "domain");

        dnsServer.contextHandler(fakeDns);
        dnsServer.start(new InetSocketAddress("127.0.0.1",53));
    }

    private Object read(EmbeddedChannel channel) throws InterruptedException {
        Object obj;
        int count=0;
        while ((obj=channel.readOutbound())==null){
            if (count>30)
                Assert.fail();
            count++;
            Thread.sleep(200);
        }
        return obj;
    }

    @Test
    public void test() throws UnknownHostException, InterruptedException {
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();

        FakeDns fakeDns = new FakeDns(eventLoopGroup.next(), new Router(Arrays.asList("kw;google;test")), new IPCIDR("192.168.1.0/24"),null,"domain");
        String targetDomain = "www.google.com";
        InetAddress realAddr = InetAddress.getByName("10.10.10.10");
        var r=new DefaultDnsResponse(0, DnsOpCode.QUERY, DnsResponseCode.NOERROR)
                .addRecord(DnsSection.ANSWER,new DefaultDnsRawRecord(targetDomain,DnsRecordType.A,3, Unpooled.wrappedBuffer(realAddr.getAddress())));

        EmbeddedChannel channel = new EmbeddedChannel(){
            @Override
            public ChannelFuture writeAndFlush(Object msg) {
                this.writeOutbound(msg);
                ChannelPromise promise = this.newPromise();
                promise.trySuccess();
                return promise;
            }
        };
        DnsContext context = new DnsContext(0, new InetSocketAddress(0), null,
                new DefaultDnsQuestion(targetDomain + ".", DnsRecordType.A), channel, question -> Future.succeededFuture(r));
        fakeDns.handle(context);

        DnsResponse response= (DnsResponse) read(channel);

        DnsRawRecord record=response.recordAt(DnsSection.ANSWER,0);
        InetAddress address = InetAddress.getByAddress(record.content().array());
        System.out.println(address);
        Assert.assertTrue(fakeDns.isFakeIp(address));

        FakeContext fakeContext = fakeDns.getFake(address);

        Assert.assertNotNull(fakeContext);

        Assert.assertTrue(fakeContext.isAvailable());
        Assert.assertEquals(address,fakeContext.getFakeAddr());

        Assert.assertEquals(targetDomain,fakeContext.getDomain());

        Assert.assertEquals("test",fakeContext.getTag());

        Assert.assertEquals(realAddr,fakeContext.getRealAddr().getAddr());

        fakeDns.handle(new DnsContext(0, new InetSocketAddress(0), null,
                new DefaultDnsQuestion("www.pixiv.net", DnsRecordType.A), channel, question -> Future.succeededFuture(r)));

        response= (DnsResponse) read(channel);
        InetAddress realAddress = InetAddress.getByAddress(((DnsRawRecord)response.recordAt(DnsSection.ANSWER,0)).content().array());
        System.out.println(realAddress);
        Assert.assertFalse(fakeDns.isFakeIp(realAddress));
        Assert.assertNull(fakeDns.getFake(realAddress));

        Thread.sleep(3000);

        Assert.assertFalse(fakeContext.isAvailable());

        Assert.assertNotNull(fakeDns.getFake(address));

    }
}
