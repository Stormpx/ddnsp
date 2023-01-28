package dns.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.crowds.dns.DnsKit;
import io.crowds.dns.cache.DnsCache;
import io.netty.buffer.Unpooled;
import io.netty.channel.DefaultEventLoop;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class CacheTest {


    @Test
    public void getTest() throws InterruptedException {

        DefaultEventLoop eventLoop = new DefaultEventLoop();
        DnsCache cache = new DnsCache(eventLoop);
        DefaultDnsRawRecord aRecord = new DefaultDnsRawRecord("foo.com.", DnsRecordType.A, 100,
                Unpooled.buffer().writeInt(42));
        cache.cache(aRecord,eventLoop);
        Thread.sleep(900);
        List<DnsRecord> records = cache.get("foo.com.", DnsRecordType.A, false);
        Assert.assertEquals(1,records.size());
        Assert.assertEquals(((DnsRawRecord)records.get(0)).content().readInt(),42);
        Assert.assertEquals(99,records.get(0).timeToLive());


        var cnameRecord = new DefaultDnsRawRecord("bar.com.", DnsRecordType.CNAME, 100,
                DnsKit.encodeDomainName("foo.com.",Unpooled.buffer()));

        cache.cache(cnameRecord,eventLoop);

        records = cache.get("bar.com.", DnsRecordType.A, true);
        Assert.assertEquals(1,records.size());
        Assert.assertEquals(((DnsRawRecord)records.get(0)).content().readInt(),42);

        records = cache.get("bar.com.", DnsRecordType.A, false);
        Assert.assertTrue(records.isEmpty());

        records = cache.get("bar.com.", DnsRecordType.CNAME, false);
        Assert.assertEquals(1,records.size());
        Assert.assertEquals(DnsKit.decodeDomainName(((DnsRawRecord)records.get(0)).content()),"foo.com.");


        var cnameRecord1 = new DefaultDnsRawRecord("test.com.", DnsRecordType.CNAME, 100,
                DnsKit.encodeDomainName("bar.com.",Unpooled.buffer()));
        cache.cache(cnameRecord1,eventLoop);
        records = cache.get("test.com.", DnsRecordType.A, true);
        Assert.assertEquals(1,records.size());
        Assert.assertEquals(((DnsRawRecord)records.get(0)).content().readInt(),42);

    }


    @Test
    public void answerTest(){

        DefaultEventLoop eventLoop = new DefaultEventLoop();
        DnsCache cache = new DnsCache(eventLoop);
        DefaultDnsRawRecord aRecord = new DefaultDnsRawRecord("foo.com.", DnsRecordType.A, 100,
                Unpooled.buffer().writeInt(42));
        cache.cache(aRecord,eventLoop);

        List<DnsRecord> result = new ArrayList<>();

        Assert.assertTrue(cache.getAnswer("foo.com.", DnsRecordType.A, false,result));
        Assert.assertEquals(1,result.size());
        Assert.assertEquals(((DnsRawRecord)result.get(0)).content().readInt(),42);

        result.clear();


        var cnameRecord = new DefaultDnsRawRecord("bar.com.", DnsRecordType.CNAME, 100,
                DnsKit.encodeDomainName("foo.com.",Unpooled.buffer()));

        cache.cache(cnameRecord,eventLoop);

        Assert.assertTrue(cache.getAnswer("bar.com.", DnsRecordType.A, true,result));
        Assert.assertEquals(2,result.size());
        Assert.assertEquals(DnsKit.decodeDomainName(((DnsRawRecord)result.get(0)).content()),"foo.com.");
        Assert.assertEquals(((DnsRawRecord)result.get(1)).content().readInt(),42);
        result.clear();

        Assert.assertFalse(cache.getAnswer("bar.com.",DnsRecordType.A,false,result));
        Assert.assertEquals(1,result.size());
        Assert.assertEquals(DnsKit.decodeDomainName(((DnsRawRecord)result.get(0)).content()),"foo.com.");
        result.clear();

        Assert.assertTrue(cache.getAnswer("bar.com.",DnsRecordType.CNAME,false,result));
        Assert.assertEquals(1,result.size());
        Assert.assertEquals(DnsKit.decodeDomainName(((DnsRawRecord)result.get(0)).content()),"foo.com.");
        result.clear();

        Assert.assertFalse(cache.getAnswer("bar.com.", DnsRecordType.AAAA, true,result));
        Assert.assertEquals(1,result.size());
        Assert.assertEquals(DnsKit.decodeDomainName(((DnsRawRecord)result.get(0)).content()),"foo.com.");
        result.clear();


        var cnameRecord1 = new DefaultDnsRawRecord("test.com.", DnsRecordType.CNAME, 100,
                DnsKit.encodeDomainName("bar.com.",Unpooled.buffer()));
        cache.cache(cnameRecord1,eventLoop);
        Assert.assertTrue(cache.getAnswer("test.com.", DnsRecordType.A, true,result));
        Assert.assertEquals(3,result.size());
        Assert.assertEquals(DnsKit.decodeDomainName(((DnsRawRecord)result.get(0)).content()),"bar.com.");
        Assert.assertEquals(DnsKit.decodeDomainName(((DnsRawRecord)result.get(1)).content()),"foo.com.");
        Assert.assertEquals(((DnsRawRecord)result.get(2)).content().readInt(),42);
        result.clear();

    }


}
