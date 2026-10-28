package proxy;

import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.TransportOption;
import io.crowds.proxy.transport.proxy.wisp.WispConnection;
import io.crowds.proxy.transport.proxy.wisp.WispOption;
import io.crowds.proxy.transport.proxy.wisp.WispProxyTransport;
import io.crowds.proxy.transport.ws.WsOption;
import io.crowds.util.Inet;
import io.crowds.util.Rands;
import io.netty.buffer.Unpooled;
import io.netty.channel.socket.DatagramPacket;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WispTest extends ProxyTestBase {

    @Rule
    public WispRule wispRule = new WispRule(CONTAINER_NETWORK);

    @Rule
    public WispRule wispV1Rule = new WispRule(CONTAINER_NETWORK);

    private WispProxyTransport createProxy(WispRule rule) {
        WispOption wispOption = new WispOption();
        wispOption.setName("wisp")
                   .setProtocol("wisp")
                   .setNetwork("ws")
                   .setTransport(new TransportOption().setWs(new WsOption()));
        var option = rule.start(wispOption);
        return new WispProxyTransport(channelCreator, (WispOption) option);
    }

    @Test
    public void tcpTest() throws Exception {
        super.tcpTest(createProxy(wispRule));
    }

    @Test
    public void motdTest() throws Exception {
        wispRule.motd("wisp-js testing motd");
        WispProxyTransport proxy = createProxy(wispRule);
        ch.qos.logback.classic.Logger wispLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(WispConnection.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        wispLogger.addAppender(appender);
        try {
            super.tcpTest(proxy);
        } finally {
            wispLogger.detachAppender(appender);
        }
        boolean motdLogged = appender.list.stream()
                .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("wisp-js testing motd"));
        Assert.assertTrue("the motd of the wisp server should be logged", motdLogged);
    }

    @Test
    public void udpTest() throws Exception {
        super.udpTest(createProxy(wispRule));
    }

    @Test
    public void tcpV1Test() throws Exception {
        wispV1Rule.wispVersion(1);
        super.tcpTest(createProxy(wispV1Rule));
    }

    @Test
    public void udpV1Test() throws Exception {
        wispV1Rule.wispVersion(1);
        super.udpTest(createProxy(wispV1Rule));
    }

    /**
     * all the connections must be multiplexed over a single websocket connection.
     */
    @Test
    public void multiplexTest() throws Exception {
        WispProxyTransport proxy = createProxy(wispRule);
        super.tcpTest(proxy);
        super.tcpTest(proxy);
        super.udpTest(proxy);

        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                futures.add(executor.submit(() -> {
                    super.tcpTest(proxy);
                    return null;
                }));
            }
            for (java.util.concurrent.Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }
    }

    /**
     * a udp stream which is refused by the server must not break the wisp connection.
     */
    @Test
    public void udpUnsupportedTest() throws Exception {
        wispRule.allowUdp(false);
        WispProxyTransport proxy = createProxy(wispRule);

        InetSocketAddress dns = new InetSocketAddress("114.114.114.114", 53);
        NetLocation location = new NetLocation(
                new NetAddr(Inet.createSocketAddress("127.0.0.1", 64424)),
                new NetAddr(dns), TP.UDP);
        EndPoint endPoint = createEndPoint(proxy, location);
        endPoint.write(new DatagramPacket(Unpooled.wrappedBuffer(Rands.genBytes(18)), dns));
        endPoint.flush();
        Thread.sleep(1000);
        endPoint.close();

        //the wisp connection must still be usable
        super.tcpTest(proxy);
    }
}
