package io.crowds.proxy.transport;

import io.crowds.Context;
import io.crowds.Ddnsp;
import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.NetAddr;
import io.crowds.proxy.NetLocation;
import io.crowds.proxy.ProxyContext;
import io.crowds.proxy.TP;
import io.crowds.proxy.transport.EndPoint;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.TlsOption;
import io.crowds.proxy.transport.Transport;
import io.crowds.proxy.transport.proxy.vless.VlessOption;
import io.crowds.proxy.transport.proxy.vless.VlessProxyTransport;
import io.crowds.proxy.transport.proxy.vmess.Security;
import io.crowds.proxy.transport.proxy.vmess.User;
import io.crowds.proxy.transport.proxy.vmess.VmessOption;
import io.crowds.proxy.transport.proxy.vmess.VmessProxyTransport;
import io.crowds.proxy.transport.proxy.xudp.XUDPOption;
import io.crowds.util.Inet;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.Future;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Shape of the full-cone support (all targets of one source address share a single underlying
 * channel), without docker: connections are replaced by EmbeddedChannel, so these cases only assert
 * whether the channel is shared and that a reused channel does not get its pipeline rebuilt.
 */
public class SharedUdpChannelsTest {

    private static final String SRC_HOST = "127.0.0.1";
    private static final int SRC_PORT = 64423;

    private static Context context;
    private static ChannelCreator channelCreator;
    private static EventLoop eventLoop;

    @BeforeClass
    public static void setUp(){
        context = Ddnsp.newContext(Ddnsp::dnsResolver);
        channelCreator = new ChannelCreator(context);
        eventLoop = context.getEventLoopGroup().next();
    }

    @AfterClass
    public static void tearDown(){
        context.getVertx().close();
    }

    private static Transport embeddedTransport(){
        return (loop, dest, preferType, delegate) -> loop.newSucceededFuture(new EmbeddedChannel());
    }

    private static VlessOption vlessOption(boolean xudp, boolean proxy443){
        VlessOption option = new VlessOption();
        option.setAddress(new InetSocketAddress("127.0.0.1", 16839))
              .setId("testtest")
              .setXudp(new XUDPOption().setEnable(xudp).setProxy443(proxy443));
        option.setProtocol("vless");
        option.setTls(new TlsOption().setEnable(false));
        return option;
    }

    private static VmessOption vmessOption(boolean xudp, boolean proxy443){
        VmessOption option = new VmessOption();
        option.setAddress(new InetSocketAddress("127.0.0.1", 16823))
              .setSecurity(Security.AES_128_GCM)
              .setUser(new User(UUID.fromString("b831381d-6324-4d53-ad4f-8cda48b30811"), 0))
              .setXudp(new XUDPOption().setEnable(xudp).setProxy443(proxy443));
        option.setProtocol("vmess");
        return option;
    }

    private static ProxyTransport vless(boolean xudp, boolean proxy443){
        return new VlessProxyTransport(channelCreator, vlessOption(xudp, proxy443)).setTransport(embeddedTransport());
    }

    private static ProxyTransport vmess(boolean xudp, boolean proxy443){
        return new VmessProxyTransport(channelCreator, vmessOption(xudp, proxy443)).setTransport(embeddedTransport());
    }

    private static NetLocation location(int srcPort, int dstPort){
        return new NetLocation(new NetAddr(Inet.createSocketAddress(SRC_HOST, srcPort)),
                new NetAddr(new InetSocketAddress("114.114.114.114", dstPort)), TP.UDP);
    }

    private static Future<EndPoint> endPoint(ProxyTransport transport, NetLocation location) throws Exception {
        ProxyContext proxyContext = new ProxyContext(eventLoop, location);
        Future<EndPoint> future = transport.createEndPoint(proxyContext);
        future.awaitUninterruptibly();
        return future;
    }

    private static EndPoint udp(ProxyTransport transport, int dstPort) throws Exception {
        Future<EndPoint> future = endPoint(transport, location(SRC_PORT, dstPort));
        assertTrue(future.isSuccess());
        return future.getNow();
    }

    @Test
    public void vlessXudpSharesChannelPerSource() throws Exception {
        ProxyTransport transport = vless(true, false);
        EndPoint first = udp(transport, 53);
        List<String> pipeline = List.copyOf(first.channel().pipeline().names());
        EndPoint second = udp(transport, 5353);

        assertSame(first.channel(), second.channel());
        //reusing a shared channel must not rebuild the pipeline: a duplicated XUDPHandler/UdpChannel
        //would scramble the frames and the reply dispatch
        assertEquals(pipeline, List.copyOf(first.channel().pipeline().names()));
    }

    @Test
    public void vlessDifferentSourceNotShared() throws Exception {
        ProxyTransport transport = vless(true, false);
        Future<EndPoint> first = endPoint(transport, location(SRC_PORT, 53));
        Future<EndPoint> second = endPoint(transport, location(SRC_PORT + 1, 53));

        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        assertNotSame(first.getNow().channel(), second.getNow().channel());
    }

    @Test
    public void vlessXudpDisabledNotShared() throws Exception {
        ProxyTransport transport = vless(false, false);
        EndPoint first = udp(transport, 53);
        EndPoint second = udp(transport, 5353);

        assertNotSame(first.channel(), second.channel());
    }

    @Test
    public void vlessPort443NotSharedByDefault() throws Exception {
        ProxyTransport transport = vless(true, false);
        EndPoint shared = udp(transport, 53);
        EndPoint quic = udp(transport, 443);
        EndPoint quic2 = udp(transport, 443);

        //443 uses native VLESS UDP (one channel per target), an existing shared channel must not be reused
        assertNotSame(shared.channel(), quic.channel());
        assertNotSame(quic.channel(), quic2.channel());
    }

    @Test
    public void vlessPort443SharedWhenProxy443Enabled() throws Exception {
        ProxyTransport transport = vless(true, true);
        EndPoint first = udp(transport, 443);
        EndPoint second = udp(transport, 443);

        assertSame(first.channel(), second.channel());
    }

    @Test
    public void vmessXudpSharesChannelPerSource() throws Exception {
        ProxyTransport transport = vmess(true, false);
        EndPoint first = udp(transport, 53);
        List<String> pipeline = List.copyOf(first.channel().pipeline().names());
        EndPoint second = udp(transport, 5353);

        assertSame(first.channel(), second.channel());
        assertEquals(pipeline, List.copyOf(first.channel().pipeline().names()));
    }

    @Test
    public void vmessPort443NotSharedByDefault() throws Exception {
        ProxyTransport transport = vmess(true, false);
        EndPoint shared = udp(transport, 53);
        EndPoint quic = udp(transport, 443);

        assertNotSame(shared.channel(), quic.channel());
    }

    @Test
    public void channelCreationFailureIsRetried() throws Exception {
        AtomicInteger openCount = new AtomicInteger();
        //the first channel creation fails, later ones succeed: the failed channel must not be cached
        Transport flaky = (loop, dest, preferType, delegate) -> openCount.incrementAndGet() == 1
                ? loop.newFailedFuture(new IOException("boom"))
                : loop.newSucceededFuture(new EmbeddedChannel());
        ProxyTransport transport = new VlessProxyTransport(channelCreator, vlessOption(true, false)).setTransport(flaky);

        Future<EndPoint> failed = endPoint(transport, location(SRC_PORT, 53));
        assertFalse(failed.isSuccess());

        EndPoint retried = udp(transport, 53);
        assertNotNull(retried.channel());
        assertEquals(2, openCount.get());
    }

}
