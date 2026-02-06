package proxy;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.TlsOption;
import io.crowds.proxy.transport.proxy.tuic.TuicConnection;
import io.crowds.proxy.transport.proxy.tuic.TuicOption;
import io.crowds.proxy.transport.proxy.tuic.TuicProxyTransport;
import io.crowds.proxy.transport.proxy.tuic.UdpMode;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;

public class TuicTest extends ProxyTestBase{

    @Rule
    public SingboxRule singboxRule = new SingboxRule(CONTAINER_NETWORK);

    protected ProxyTransport createProxy(ChannelCreator channelCreator,UdpMode udpMode) throws IOException {
        InetSocketAddress dest = new InetSocketAddress("localhost", 16845);
        ProtocolOption option=singboxRule.start(
                new TuicOption()
                        .setAddress(dest)
                        .setUuid(UUID.randomUUID())
                        .setPassword("passpasspass")
                        .setUdpMode(udpMode)
                        .setName("tuic")
                        .setTls(new TlsOption().setEnable(true).setAllowInsecure(true).setAlpn(List.of("h3")))
        );

//        InetSocketAddress dest = new InetSocketAddress("192.168.31.207", 8443);
//        ProtocolOption option=new TuicOption()
//                .setAddress(dest)
//                .setUuid(UUID.fromString("b41b7cf1-9e15-487a-8a59-b001d820b864"))
//                .setPassword("fuck")
//                .setUdpMode(UdpMode.QUIC)
//                .setName("tuic")
//                .setTls(new TlsOption().setEnable(true).setAllowInsecure(true));

        return new TuicProxyTransport(channelCreator, (TuicOption) option);

    }


    @Test
    public void tcpTest() throws Exception {
        TuicProxyTransport proxy = (TuicProxyTransport) createProxy(channelCreator, UdpMode.NATIVE);
        super.tcpTest(proxy);
        TuicConnection connection = proxy.getConnection().sync().get();
        connection.close().sync();

        Thread.sleep(50);
        super.tcpTest(proxy);

    }

    @Test
    public void nativeUdpTest() throws Exception {
        super.udpTest(createProxy(channelCreator,UdpMode.NATIVE));
    }

    @Test
    public void quicUdpTest() throws Exception {
        super.udpTest(createProxy(channelCreator,UdpMode.QUIC));
    }


}
