package io.crowds.proxy.transport.proxy.vmess;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.TransportOption;
import io.crowds.proxy.transport.proxy.vmess.Security;
import io.crowds.proxy.transport.proxy.vmess.User;
import io.crowds.proxy.transport.proxy.vmess.VmessOption;
import io.crowds.proxy.transport.proxy.vmess.VmessProxyTransport;
import io.crowds.proxy.transport.proxy.xudp.XUDPOption;
import io.crowds.proxy.transport.ws.WsOption;
import io.crowds.support.ProxyTestBase;
import io.crowds.support.XrayRule;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class VmessTest extends ProxyTestBase {

    @Rule
    public XrayRule xrayRule = new XrayRule(CONTAINER_NETWORK);

    protected ProxyTransport createProxy(boolean xudp,ChannelCreator channelCreator) throws IOException {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16823);
        var option = xrayRule.start(new VmessOption()
                .setAddress(dest)
                .setSecurity(Security.AES_128_GCM)
                .setUser(new User(UUID.fromString("b831381d-6324-4d53-ad4f-8cda48b30811"),0))
                .setXudp(new XUDPOption().setEnable(xudp))
        );
        return new VmessProxyTransport(channelCreator, (VmessOption) option);
    }

    protected ProxyTransport createProxyWithWs(boolean xudp,ChannelCreator channelCreator) throws IOException {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16825);
        var option= xrayRule.start(new VmessOption()
                .setAddress(dest)
                .setSecurity(Security.AES_128_GCM)
                .setUser(new User(UUID.fromString("b831381d-6324-4d53-ad4f-8cda48b30811"),0))
                .setNetwork("ws")
                .setTransport(new TransportOption()
                        .setWs(new WsOption())));
        return new VmessProxyTransport(channelCreator, (VmessOption) option);
    }


    @Test
    public void tcpTest() throws Exception {
        super.tcpTest(createProxy(false,channelCreator));
    }

    @Test
    public void websocketTest() throws Exception {
        super.tcpTest(createProxyWithWs(false,channelCreator));
    }

    @Test
    public void udpTest() throws Exception {
        super.udpTest(createProxy(false,channelCreator));
    }

    @Test
    public void websocketUdpTest() throws Exception {
        super.udpTest(createProxyWithWs(false,channelCreator));
    }

    @Test
    public void xudpTest() throws Exception {
        super.udpTest(createProxy(true,channelCreator));
    }

    @Test
    public void websocketXUdpTest() throws Exception {
        super.udpTest(createProxyWithWs(true,channelCreator));
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

    /**
     * Without XUDP this is native VMess UDP (one connection per target), not full-cone
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
