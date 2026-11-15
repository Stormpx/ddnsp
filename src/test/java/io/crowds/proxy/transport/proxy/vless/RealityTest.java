package io.crowds.proxy.transport.proxy.vless;

import io.crowds.proxy.DomainNetAddr;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.compoments.tls.reality.RealityOption;
import io.crowds.proxy.transport.TlsOption;
import io.crowds.proxy.transport.proxy.xudp.XUDPOption;
import io.crowds.support.ProxyTestBase;
import io.crowds.support.RealityKeys;
import io.crowds.support.XrayRule;
import io.netty.buffer.Unpooled;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class RealityTest extends ProxyTestBase {

    private static final String SERVER_NAME = "test.ddnsp.org";
    private static final String REALITY_DEST = "nginx:443";
    private static final NetAddr DNS_114 = new NetAddr(new InetSocketAddress("114.114.114.114", 53));
    private static final NetAddr DNS_115 = new NetAddr(new InetSocketAddress("114.114.115.115", 53));

    private final RealityKeys realityKeys = RealityKeys.generate();

    @Rule
    public final XrayRule xrayRule = new XrayRule(CONTAINER_NETWORK)
            .setReality(realityKeys, REALITY_DEST, SERVER_NAME);

    private ProxyTransport createProxy(RealityKeys keys, Vless.Flow flow, boolean xudp) throws IOException {
        var option = xrayRule.start(new VlessOption()
                .setAddress(new InetSocketAddress("127.0.0.1", 16843))
                .setId("testtest")
                .setXudp(new XUDPOption().setEnable(xudp))
                .setFlow(flow.value())
                .setProtocol("vless")
                .setTls(new TlsOption()
                        .setEnable(true)
                        .setServerName(SERVER_NAME)
                        .setReality(new RealityOption()
                                .setPublicKey(keys.publicKey())
                                .setShortId(keys.shortId()))));
        return new VlessProxyTransport(channelCreator, (VlessOption) option);
    }

    @Test
    public void tcpTest() throws Exception {
        super.tcpTest(createProxy(realityKeys, Vless.Flow.NONE, false));
    }

    @Test
    public void tcpXRVTest() throws Exception {
        super.tcpTest(createProxy(realityKeys, Vless.Flow.XRV, false));
    }

    @Test
    public void udpTest() throws Exception {
        super.udpTest(createProxy(realityKeys, Vless.Flow.NONE, false));
    }

    @Test
    public void xudpTest() throws Exception {
        super.udpTest(createProxy(realityKeys, Vless.Flow.NONE, true));
    }

    @Test
    public void xudpFullConeTest() throws Exception {
        List<EndPoint> endPoints = udpMultiTargetTest(createProxy(realityKeys, Vless.Flow.NONE, true), DNS_114, DNS_115);
        try {
            assertSame(endPoints.get(0).channel(), endPoints.get(1).channel());
        } finally {
            endPoints.forEach(EndPoint::close);
        }
    }

    @Test
    public void udpNoShareTest() throws Exception {
        List<EndPoint> endPoints = udpMultiTargetTest(createProxy(realityKeys, Vless.Flow.NONE, false), DNS_114, DNS_115);
        try {
            assertNotSame(endPoints.get(0).channel(), endPoints.get(1).channel());
        } finally {
            endPoints.forEach(EndPoint::close);
        }
    }

    /**
     * A client that cannot derive the shared authentication key is handled as unauthenticated traffic
     * by the server and must not end up with a usable tunnel.
     */
    @Test
    public void wrongPublicKeyTest() throws Exception {
        expectFailure(createProxy(RealityKeys.generate(), Vless.Flow.NONE, false));
    }

    @Test
    public void wrongShortIdTest() throws Exception {
        RealityKeys wrongShortId = RealityKeys.of(realityKeys.privateKey(), RealityKeys.generate().shortId());
        expectFailure(createProxy(wrongShortId, Vless.Flow.NONE, false));
    }

    private void expectFailure(ProxyTransport proxyTransport) throws Exception {
        NetLocation location = new NetLocation(new NetAddr(new InetSocketAddress("127.0.0.1", 0)),
                new DomainNetAddr("nginx", 443), TP.TCP);
        EndPoint endPoint = createEndPoint(proxyTransport, location);
        CompletableFuture<Void> closed = new CompletableFuture<>();
        endPoint.closeFuture().addListener(f -> closed.complete(null));
        endPoint.write(Unpooled.wrappedBuffer("GET /1M.data HTTP/1.1\r\nHost: test.ddnsp.org\r\n\r\n"
                .getBytes(StandardCharsets.US_ASCII)));
        endPoint.flush();
        closed.get(20, TimeUnit.SECONDS);
    }
}
