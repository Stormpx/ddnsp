package proxy;

import io.crowds.proxy.ChannelCreator;
import io.crowds.proxy.transport.ProtocolOption;
import io.crowds.proxy.transport.ProxyTransport;
import io.crowds.proxy.transport.proxy.socks.SocksOption;
import io.crowds.proxy.transport.proxy.socks.SocksProxyTransport;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

public class SocksTest extends ProxyTestBase {

    @Rule
    public XrayRule xrayRule = new XrayRule(CONTAINER_NETWORK);

    protected ProxyTransport createProxy(ChannelCreator channelCreator) throws IOException {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16837);
        ProtocolOption option=xrayRule.start(
                new SocksOption()
                        .setRemote(dest)
                        .setName("socks")
        );
        return new SocksProxyTransport(channelCreator, (SocksOption) option);
    }

    protected ProxyTransport createProxyForUdp(ChannelCreator channelCreator) throws IOException {
        InetSocketAddress dest = new InetSocketAddress("127.0.0.1", 16836);
        ProtocolOption option= new SocksOption()
                .setRemote(dest)
                .setName("socks");
        return new SocksProxyTransport(channelCreator, (SocksOption) option);
    }

    @Test
    public void tcpTest() throws Exception {
        super.tcpTest(createProxy(channelCreator));
    }


    @Test
    public void udpTest() throws Exception {
        super.udpTest(createProxy(channelCreator));
    }

}
