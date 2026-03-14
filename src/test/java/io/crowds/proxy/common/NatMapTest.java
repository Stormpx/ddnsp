package io.crowds.proxy.common;

import io.crowds.proxy.DomainNetAddr;
import io.crowds.proxy.NetAddr;
import junit.framework.TestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

public class NatMapTest {

    @Test
    public void simpleTest() {

        NatMap natMap = new NatMap();
        natMap.add("foo.bar","bar.foo");
        natMap.add("1.1.1.1/8","aaa.bbb");
        natMap.add("2.2.2.2","3.3.3.3");
        natMap.add("4.4.4.4","4.4.4.4:10000");

        assertEquals(new DomainNetAddr("bar.foo",1234),natMap.translate(new DomainNetAddr("foo.bar",1234)));
        assertEquals(NetAddr.of("aaa.bbb",1234),natMap.translate(NetAddr.of("1.1.1.2",1234)));
        assertEquals(NetAddr.of("3.3.3.3",1234),natMap.translate(NetAddr.of("2.2.2.2",1234)));
        assertNull(natMap.translate(NetAddr.of("2.2.2.3", 1234)));
        assertEquals(NetAddr.of("4.4.4.4",10000),natMap.translate(NetAddr.of("4.4.4.4",1234)));

    }

    @Test
    public void testPortOnlyTranslation() {
        NatMap natMap = new NatMap();
        natMap.add("10.0.0.1", "8080");

        NetAddr result = natMap.translate(NetAddr.of("10.0.0.1", 1234));
        assertNotNull(result);
        assertEquals("10.0.0.1", result.getHost());
        assertEquals(8080, result.getPort());
    }

    @Test
    public void testHostOnlyTranslation() {
        NatMap natMap = new NatMap();
        natMap.add("192.168.1.1", "proxy.example.com");

        NetAddr result = natMap.translate(NetAddr.of("192.168.1.1", 8888));
        assertNotNull(result);
        assertEquals("proxy.example.com", result.getHost());
        assertEquals(8888, result.getPort());
    }

    @Test
    public void testFullAddressTranslation() {
        NatMap natMap = new NatMap();
        natMap.add("172.16.0.1", "10.0.0.1:9000");

        NetAddr result = natMap.translate(NetAddr.of("172.16.0.1", 1234));
        assertNotNull(result);
        assertEquals("10.0.0.1", result.getHost());
        assertEquals(9000, result.getPort());
    }

    @Test
    public void testDomainCaseInsensitive() {
        NatMap natMap = new NatMap();
        natMap.add("example.com", "target.example.org");

        assertEquals(
            new DomainNetAddr("target.example.org", 8080),
            natMap.translate(new DomainNetAddr("EXAMPLE.COM", 8080))
        );
        assertEquals(
            new DomainNetAddr("target.example.org", 8080),
            natMap.translate(new DomainNetAddr("Example.Com", 8080))
        );
    }

    @Test
    public void testDomainWithPortTranslation() {
        NatMap natMap = new NatMap();
        natMap.add("source.domain.com", "target.domain.com:443");

        NetAddr result = natMap.translate(new DomainNetAddr("source.domain.com", 80));
        assertNotNull(result);
        assertEquals("target.domain.com", result.getHost());
        assertEquals(443, result.getPort());
    }

    @Test
    public void testCidr16Subnet() {
        NatMap natMap = new NatMap();
        natMap.add("192.168.0.0/16", "10.0.0.1");

        assertEquals(NetAddr.of("10.0.0.1", 1234), natMap.translate(NetAddr.of("192.168.0.1", 1234)));
        assertEquals(NetAddr.of("10.0.0.1", 1234), natMap.translate(NetAddr.of("192.168.255.254", 1234)));
        assertNull(natMap.translate(NetAddr.of("192.167.0.1", 1234)));
    }

    @Test
    public void testCidr24Subnet() {
        NatMap natMap = new NatMap();
        natMap.add("172.16.5.0/24", "gateway.crowds.io");

        assertEquals(NetAddr.of("gateway.crowds.io", 8080), natMap.translate(NetAddr.of("172.16.5.0", 8080)));
        assertEquals(NetAddr.of("gateway.crowds.io", 8080), natMap.translate(NetAddr.of("172.16.5.128", 8080)));
        assertEquals(NetAddr.of("gateway.crowds.io", 8080), natMap.translate(NetAddr.of("172.16.5.255", 8080)));
        assertNull(natMap.translate(NetAddr.of("172.16.4.1", 8080)));
    }

    @Test
    public void testNoMatchingRule() {
        NatMap natMap = new NatMap();
        natMap.add("10.0.0.1/8", "192.168.1.1");

        assertNull(natMap.translate(NetAddr.of("8.8.8.8", 53)));
        assertNull(natMap.translate(new DomainNetAddr("unknown.domain.com", 80)));
    }

    @Test
    public void testMultipleRules() {
        NatMap natMap = new NatMap();
        natMap.add("example.com", "proxy1.example.com");
        natMap.add("test.com", "proxy2.example.com:8080");
        natMap.add("10.0.0.0/8", "172.16.0.1");
        natMap.add("10.0.0.0/16", "172.16.0.2");
        natMap.add("192.168.1.1", "192.168.1.100");

        assertEquals(
            new DomainNetAddr("proxy1.example.com", 80),
            natMap.translate(new DomainNetAddr("example.com", 80))
        );
        assertEquals(
            NetAddr.of("proxy2.example.com", 8080),
            natMap.translate(new DomainNetAddr("test.com", 80))
        );
        assertEquals(
            NetAddr.of("172.16.0.1", 443),
            natMap.translate(NetAddr.of("10.1.2.3", 443))
        );
        assertEquals(
                NetAddr.of("172.16.0.2", 443),
                natMap.translate(NetAddr.of("10.0.2.3", 443))
        );
        assertEquals(
            NetAddr.of("192.168.1.100", 9090),
            natMap.translate(NetAddr.of("192.168.1.1", 9090))
        );
    }

    @Test(expected = NullPointerException.class)
    public void testNullPattern() {
        NatMap natMap = new NatMap();
        natMap.add(null, "target.com");
    }

    @Test(expected = NullPointerException.class)
    public void testNullResult() {
        NatMap natMap = new NatMap();
        natMap.add("source.com", null);
    }

    @Test
    public void testExactIPMatch() {
        NatMap natMap = new NatMap();
        natMap.add("8.8.8.8", "1.2.3.4");

        assertEquals(NetAddr.of("1.2.3.4", 53), natMap.translate(NetAddr.of("8.8.8.8", 53)));
        assertNull(natMap.translate(NetAddr.of("8.8.8.9", 53)));
    }
}