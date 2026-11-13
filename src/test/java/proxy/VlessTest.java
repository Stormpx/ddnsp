package proxy;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.TlsOption;
import io.crowds.proxy.transport.proxy.vless.Vless;
import io.crowds.proxy.transport.proxy.vless.VlessOption;
import io.crowds.proxy.transport.proxy.vless.VlessProxyTransport;
import io.crowds.proxy.transport.proxy.vless.VlessUUID;
import io.crowds.proxy.transport.proxy.xudp.XUDPOption;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.Assert.*;

public class VlessTest extends ProxyTestBase {

    @Rule
    public XrayRule xrayRule = new XrayRule(CONTAINER_NETWORK);



    protected ProxyTransport createProxy(boolean xudp,ChannelCreator channelCreator) throws IOException {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16839);
        var option= xrayRule.start(new VlessOption()
                .setAddress(dest)
                .setXudp(new XUDPOption().setEnable(xudp))
                .setId("testtest")
                .setProtocol("vless")
                .setTls(new TlsOption().setEnable(false)));
        return new VlessProxyTransport(channelCreator, (VlessOption) option);
    }

    protected ProxyTransport createProxy(ChannelCreator channelCreator) throws IOException {
        return createProxy(false,channelCreator);
    }

    protected ProxyTransport createProxyXRV(boolean xudp,ChannelCreator channelCreator) throws IOException {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16841);
        var option= xrayRule.start(new VlessOption()
                .setAddress(dest)
                .setXudp(new XUDPOption().setEnable(xudp))
                .setId("testtest")
                .setFlow(Vless.Flow.XRV.value())
                .setProtocol("vless")
                .setTls(new TlsOption().setEnable(true).setAllowInsecure(true)));
        return new VlessProxyTransport(channelCreator, (VlessOption) option);
    }




    protected ProxyTransport createProxyXRV(ChannelCreator channelCreator) throws IOException {
        return createProxyXRV(false,channelCreator);
    }

    @Test
    public void uuidTest(){
//        System.out.println(HexFormat.ofDelimiter("-").formatHex(Rands.genBytes(8),0,4));
        assertEquals("feb54431-301b-52bb-a6dd-e1e93e81bb9e",VlessUUID.of("example").toString());
        assertEquals("5783a3e7-e373-51cd-8642-c83782b807c5",VlessUUID.of("我爱\uD83C\uDF49老师1314").toString());
    }

    @Test
    public void tcpTest() throws Exception {
        super.tcpTest(createProxy(channelCreator));
    }

    @Test
    public void tcpXRVTest() throws Exception {
        super.tcpTest(createProxyXRV(channelCreator));
    }

    @Test
    public void udpTest() throws Exception {
        super.udpTest(createProxy(channelCreator));
    }

    @Test
    public void udpXRVTest() throws Exception {
        //also works, but flow is none
        super.udpTest(createProxyXRV(channelCreator));
    }

    @Test
    public void xudpTest() throws Exception {
        super.udpTest(createProxy(true,channelCreator));
    }

    @Test
    public void xudpXRVTest() throws Exception {
        //also works, but flow is none
        super.udpTest(createProxyXRV(true,channelCreator));
    }

    private static final NetAddr DNS_114 = new NetAddr(new InetSocketAddress("114.114.114.114",53));
    private static final NetAddr DNS_115 = new NetAddr(new InetSocketAddress("114.114.115.115",53));

    /**
     * With XUDP all targets of one source address share a single connection (full-cone)
     */
    @Test
    public void xudpFullConeTest() throws Exception {
        List<EndPoint> endPoints = udpMultiTargetTest(createProxy(true,channelCreator),DNS_114,DNS_115);
        try {
            assertSame(endPoints.get(0).channel(),endPoints.get(1).channel());
        } finally {
            endPoints.forEach(EndPoint::close);
        }
    }

    @Test
    public void xudpXRVFullConeTest() throws Exception {
        List<EndPoint> endPoints = udpMultiTargetTest(createProxyXRV(true,channelCreator),DNS_114,DNS_115);
        try {
            assertSame(endPoints.get(0).channel(),endPoints.get(1).channel());
        } finally {
            endPoints.forEach(EndPoint::close);
        }
    }

    /**
     * Without XUDP this is native VLESS UDP (one connection per target), not full-cone
     */
    @Test
    public void udpNoShareTest() throws Exception {
        List<EndPoint> endPoints = udpMultiTargetTest(createProxy(false,channelCreator),DNS_114,DNS_115);
        try {
            assertNotSame(endPoints.get(0).channel(),endPoints.get(1).channel());
        } finally {
            endPoints.forEach(EndPoint::close);
        }
    }
}
